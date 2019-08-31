package brs

import brs.crypto.Crypto
import brs.fluxcapacitor.FluxCapacitor
import brs.fluxcapacitor.FluxValues
import brs.props.PropertyService
import brs.props.Props
import brs.services.TimeService
import brs.util.Convert
import brs.util.Listeners
import brs.util.MiningPlot
import brs.util.ThreadPool
import burst.kit.crypto.BurstCrypto
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.BigInteger
import java.util.Collections
import kotlin.collections.Map.Entry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

open class GeneratorImpl(private val dp: DependencyProvider) : Generator {

    private val listeners = Listeners<Generator.GeneratorState, Generator.Event>()
    private val generators = ConcurrentHashMap<Long, GeneratorStateImpl>()
    private val burstCrypto = BurstCrypto.getInstance()

    override val allGenerators: Collection<Generator.GeneratorState>
        get() = Collections.unmodifiableCollection<Generator.GeneratorState>(generators.values)

    private fun generateBlockThread(blockchainProcessor: BlockchainProcessor): Runnable {
        return {
            if (blockchainProcessor.isScanning) {
                return
            }
            try {
                val currentBlock = dp.blockchain.lastBlock.height.toLong()
                val it = generators.entries.iterator()
                while (it.hasNext() && !Thread.currentThread().isInterrupted && ThreadPool.running.get()) {
                    val generator = it.next()
                    if (currentBlock < generator.value.block) {
                        generator.value.forge(blockchainProcessor)
                    } else {
                        it.remove()
                    }
                }
            } catch (e: BlockchainProcessor.BlockNotAcceptedException) {
                logger.debug("Error in block generation thread", e)
            }
        }
    }

    override fun generateForBlockchainProcessor(dp: DependencyProvider) {
        dp.threadPool.scheduleThread("GenerateBlocks", generateBlockThread(dp.blockchainProcessor), 500, TimeUnit.MILLISECONDS)
    }

    override fun addListener(listener: Consumer<Generator.GeneratorState>, eventType: Generator.Event): Boolean {
        return listeners.addListener(listener, eventType)
    }

    override fun removeListener(listener: Consumer<Generator.GeneratorState>, eventType: Generator.Event): Boolean {
        return listeners.removeListener(listener, eventType)
    }

    override fun addNonce(secretPhrase: String, nonce: Long?): Generator.GeneratorState {
        val publicKey = Crypto.getPublicKey(secretPhrase)
        return addNonce(secretPhrase, nonce, publicKey)
    }

    override fun addNonce(secretPhrase: String, nonce: Long?, publicKey: ByteArray): Generator.GeneratorState {
        val publicKeyHash = Crypto.sha256().digest(publicKey)
        val id = Convert.fullHashToId(publicKeyHash)

        val generator = GeneratorStateImpl(secretPhrase, nonce, publicKey, id)
        val curGen = generators[id]
        if (curGen == null || generator.block > curGen.block || generator.deadline.compareTo(curGen.deadline) < 0) {
            generators[id] = generator
            listeners.accept(generator, Generator.Event.NONCE_SUBMITTED)
            if (logger.isDebugEnabled) {
                logger.debug("Account {} started mining, deadline {} seconds", Convert.toUnsignedLong(id), generator.deadline)
            }
        } else {
            if (logger.isDebugEnabled) {
                logger.debug("Account {} already has a better nonce", Convert.toUnsignedLong(id))
            }
        }

        return generator
    }

    override fun calculateGenerationSignature(lastGenSig: ByteArray, lastGenId: Long): ByteArray {
        return burstCrypto.calculateGenerationSignature(lastGenSig, lastGenId)
    }

    override fun calculateScoop(genSig: ByteArray, height: Long): Int {
        return burstCrypto.calculateScoop(genSig, height)
    }

    private fun getPocVersion(blockHeight: Int): Int {
        return if (dp.fluxCapacitor.getValue(FluxValues.POC2, blockHeight)) 2 else 1
    }

    override fun calculateHit(accountId: Long, nonce: Long, genSig: ByteArray, scoop: Int, blockHeight: Int): BigInteger {
        return burstCrypto.calculateHit(accountId, nonce, genSig, scoop, getPocVersion(blockHeight))
    }

    override fun calculateHit(accountId: Long, nonce: Long, genSig: ByteArray, scoopData: ByteArray): BigInteger {
        return burstCrypto.calculateHit(accountId, nonce, genSig, scoopData)
    }

    override fun calculateDeadline(accountId: Long, nonce: Long, genSig: ByteArray, scoop: Int, baseTarget: Long, blockHeight: Int): BigInteger {
        return burstCrypto.calculateDeadline(accountId, nonce, genSig, scoop, baseTarget, getPocVersion(blockHeight))
    }

    inner class GeneratorStateImpl private constructor(private val secretPhrase: String, private val nonce: Long?, override val publicKey: ByteArray, override val accountId: Long?) : Generator.GeneratorState {
        override val deadline: BigInteger
        override val block: Long

        init {

            val lastBlock = dp.blockchain.lastBlock

            this.block = lastBlock.height.toLong() + 1

            val lastGenSig = lastBlock.generationSignature
            val lastGenerator = lastBlock.generatorId

            val newGenSig = calculateGenerationSignature(lastGenSig, lastGenerator)

            val scoopNum = calculateScoop(newGenSig, lastBlock.height + 1L)

            deadline = calculateDeadline(accountId!!, nonce!!, newGenSig, scoopNum, lastBlock.baseTarget, lastBlock.height + 1)
        }// need to store publicKey in addition to accountId, because the account may not have had its publicKey set yet

        @Throws(BlockchainProcessor.BlockNotAcceptedException::class)
        private fun forge(blockchainProcessor: BlockchainProcessor) {
            val lastBlock = dp.blockchain.lastBlock

            val elapsedTime = dp.timeService.epochTime - lastBlock.timestamp
            if (BigInteger.valueOf(elapsedTime.toLong()).compareTo(deadline) > 0) {
                blockchainProcessor.generateBlock(secretPhrase, publicKey, nonce)
            }
        }
    }

    class MockGenerator(private val dp: DependencyProvider) : GeneratorImpl(dp) {

        override fun calculateHit(accountId: Long, nonce: Long, genSig: ByteArray, scoop: Int, blockHeight: Int): BigInteger {
            return BigInteger.valueOf(dp.propertyService.get(Props.DEV_MOCK_MINING_DEADLINE).toLong())
        }

        override fun calculateHit(accountId: Long, nonce: Long, genSig: ByteArray, scoopData: ByteArray): BigInteger {
            return BigInteger.valueOf(dp.propertyService.get(Props.DEV_MOCK_MINING_DEADLINE).toLong())
        }

        override fun calculateDeadline(accountId: Long, nonce: Long, genSig: ByteArray, scoop: Int, baseTarget: Long, blockHeight: Int): BigInteger {
            return BigInteger.valueOf(dp.propertyService.get(Props.DEV_MOCK_MINING_DEADLINE).toLong())
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GeneratorImpl::class.java)
    }
}

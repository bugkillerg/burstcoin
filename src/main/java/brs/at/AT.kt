/*
 * Some portion .. Copyright (c) 2014 CIYAM Developers

 Distributed under the MIT/X11 software license, please refer to the file LICENSE.txt
*/

package brs.at

import brs.*
import brs.db.BurstKey
import brs.db.TransactionDb
import brs.db.VersionedEntityTable
import brs.services.AccountService

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayList
import java.util.Arrays
import java.util.LinkedHashMap
import java.util.function.Consumer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class AT : AtMachineState {
    val dbKey: BurstKey
    val name: String
    val description: String
    private val nextHeight: Int

    private constructor(dp: DependencyProvider, atId: ByteArray, creator: ByteArray, name: String, description: String, creationBytes: ByteArray, height: Int) : super(dp, atId, creator, creationBytes, height) {
        this.name = name
        this.description = description
        dbKey = atDbKeyFactory(dp).newKey(AtApiHelper.getLong(atId))
        this.nextHeight = dp.blockchain.height
    }

    constructor(dp: DependencyProvider, atId: ByteArray, creator: ByteArray, name: String, description: String, version: Short,
                stateBytes: ByteArray, csize: Int, dsize: Int, cUserStackBytes: Int, cCallStackBytes: Int,
                creationBlockHeight: Int, sleepBetween: Int, nextHeight: Int,
                freezeWhenSameBalance: Boolean, minActivationAmount: Long, apCode: ByteArray) : super(dp, atId, creator, version,
            stateBytes, csize, dsize, cUserStackBytes, cCallStackBytes,
            creationBlockHeight, sleepBetween,
            freezeWhenSameBalance, minActivationAmount, apCode) {
        this.name = name
        this.description = description
        dbKey = atDbKeyFactory(dp).newKey(AtApiHelper.getLong(atId))
        this.nextHeight = nextHeight
    }

    private fun atStateTable(): VersionedEntityTable<ATState> {
        return dp.atStore.atStateTable
    }

    fun saveState() {
        var state: ATState? = atStateTable().get(atStateDbKeyFactory(dp).newKey(AtApiHelper.getLong(this.id!!)))
        val prevHeight = dp.blockchain.height
        val newNextHeight = prevHeight + waitForNumberOfBlocks
        if (state != null) {
            state.state = state
            state.prevHeight = prevHeight
            state.nextHeight = newNextHeight
            state.sleepBetween = sleepBetween
            state.prevBalance = getpBalance()
            state.freezeWhenSameBalance = freezeOnSameBalance()
            state.minActivationAmount = minActivationAmount()
        } else {
            state = ATState(dp, AtApiHelper.getLong(this.id!!),
                    state, newNextHeight, sleepBetween,
                    getpBalance()!!, freezeOnSameBalance(), minActivationAmount())
        }
        atStateTable().insert(state)
    }

    fun nextHeight(): Int {
        return nextHeight
    }

    class HandleATBlockTransactionsListener(private val dp: DependencyProvider) : Consumer<Block> {

        override fun accept(block: Block) {
            pendingFees.forEach { (key, value) ->
                val atAccount = dp.accountService.getAccount(key)
                dp.accountService.addToBalanceAndUnconfirmedBalanceNQT(atAccount, -value)
            }

            val transactions = ArrayList<Transaction>()
            for (atTransaction in pendingTransactions) {
                dp.accountService.addToBalanceAndUnconfirmedBalanceNQT(dp.accountService.getAccount(AtApiHelper.getLong(atTransaction.senderId)), -atTransaction.amount)
                dp.accountService.addToBalanceAndUnconfirmedBalanceNQT(dp.accountService.getOrAddAccount(AtApiHelper.getLong(atTransaction.recipientId)), atTransaction.amount!!)

                val builder = Transaction.Builder(dp, 1.toByte(), Genesis.creatorPublicKey,
                        atTransaction.amount!!, 0L, block.timestamp, 1440.toShort(), Attachment.AT_PAYMENT)

                builder.senderId(AtApiHelper.getLong(atTransaction.senderId))
                        .recipientId(AtApiHelper.getLong(atTransaction.recipientId))
                        .blockId(block.id)
                        .height(block.height)
                        .blockTimestamp(block.timestamp)
                        .ecBlockHeight(0)
                        .ecBlockId(0L)

                val message = atTransaction.message
                if (message != null) {
                    builder.message(Appendix.Message(message, dp.blockchain.height))
                }

                try {
                    val transaction = builder.build()
                    if (!dp.dbs.transactionDb.hasTransaction(transaction.id)) {
                        transactions.add(transaction)
                    }
                } catch (e: BurstException.NotValidException) {
                    throw RuntimeException("Failed to construct AT payment transaction", e)
                }

            }

            if (!transactions.isEmpty()) {
                // WATCH: Replace after transactions are converted!
                dp.dbs.transactionDb.saveTransactions(transactions)
            }
        }
    }

    open class ATState(dp: DependencyProvider, val atId: Long, state: ByteArray,
                       nextHeight: Int, sleepBetween: Int, prevBalance: Long, freezeWhenSameBalance: Boolean, minActivationAmount: Long) {

        val dbKey: BurstKey
        var state: ByteArray? = null
            internal set
        var prevHeight: Int = 0
            internal set
        var nextHeight: Int = 0
            internal set
        var sleepBetween: Int = 0
            internal set
        var prevBalance: Long = 0
            internal set
        var freezeWhenSameBalance: Boolean = false
            internal set
        var minActivationAmount: Long = 0
            internal set

        init {
            this.dbKey = atStateDbKeyFactory(dp).newKey(this.atId)
            this.state = state
            this.nextHeight = nextHeight
            this.sleepBetween = sleepBetween
            this.prevBalance = prevBalance
            this.freezeWhenSameBalance = freezeWhenSameBalance
            this.minActivationAmount = minActivationAmount
        }
    }

    companion object {
        private val pendingFees = LinkedHashMap<Long, Long>()
        private val pendingTransactions = ArrayList<AtTransaction>()

        fun clearPendingFees() {
            pendingFees.clear()
        }

        fun clearPendingTransactions() {
            pendingTransactions.clear()
        }

        fun addPendingFee(id: Long, fee: Long) {
            pendingFees[id] = fee
        }

        fun addPendingFee(id: ByteArray, fee: Long) {
            addPendingFee(AtApiHelper.getLong(id), fee)
        }

        fun addPendingTransaction(atTransaction: AtTransaction) {
            pendingTransactions.add(atTransaction)
        }

        fun findPendingTransaction(recipientId: ByteArray): Boolean {
            for (tx in pendingTransactions) {
                if (Arrays.equals(recipientId, tx.recipientId)) {
                    return true
                }
            }
            return false
        }

        // TODO stop passing dp around, fix this code to be organized properly!!!

        private fun atDbKeyFactory(dp: DependencyProvider): BurstKey.LongKeyFactory<AT> {
            return dp.atStore.atDbKeyFactory
        }

        private fun atTable(dp: DependencyProvider): VersionedEntityTable<AT> {
            return dp.atStore.atTable
        }

        private fun atStateDbKeyFactory(dp: DependencyProvider): BurstKey.LongKeyFactory<ATState> {
            return dp.atStore.atStateDbKeyFactory
        }

        fun getAT(dp: DependencyProvider, id: ByteArray): AT {
            return getAT(dp, AtApiHelper.getLong(id))
        }

        fun getAT(dp: DependencyProvider, id: Long?): AT {
            return dp.atStore.getAT(id)
        }

        fun addAT(dp: DependencyProvider, atId: Long?, senderAccountId: Long?, name: String, description: String, creationBytes: ByteArray, height: Int) {
            val bf = ByteBuffer.allocate(8 + 8)
            bf.order(ByteOrder.LITTLE_ENDIAN)

            bf.putLong(atId!!)

            val id = ByteArray(8)

            bf.putLong(8, senderAccountId!!)

            val creator = ByteArray(8)
            bf.clear()
            bf.get(id, 0, 8)
            bf.get(creator, 0, 8)

            val at = AT(dp, id, creator, name, description, creationBytes, height)

            AtController.resetMachine(at)

            atTable(dp).insert(at)

            at.saveState()

            val account = Account.getOrAddAccount(dp, atId)
            account.apply(dp, ByteArray(32), height)
        }

        // TODO just do it yourself! or add a utils class or something... same goes for all of the methods around here doing this
        fun getOrderedATs(dp: DependencyProvider): List<Long> {
            return dp.atStore.orderedATs
        }

        fun compressState(stateBytes: ByteArray?): ByteArray? {
            if (stateBytes == null || stateBytes.size == 0) {
                return null
            }

            try {
                ByteArrayOutputStream().use { bos ->
                    GZIPOutputStream(bos).use { gzip ->
                        gzip.write(stateBytes)
                        gzip.flush()
                    }
                    return bos.toByteArray()
                }
            } catch (e: IOException) {
                throw RuntimeException(e.message, e)
            }

        }

        fun decompressState(stateBytes: ByteArray?): ByteArray? {
            if (stateBytes == null || stateBytes.size == 0) {
                return null
            }

            try {
                ByteArrayInputStream(stateBytes).use { bis ->
                    GZIPInputStream(bis).use { gzip ->
                        ByteArrayOutputStream().use { bos ->
                            val buffer = ByteArray(256)
                            var read: Int
                            while ((read = gzip.read(buffer, 0, buffer.size)) > 0) {
                                bos.write(buffer, 0, read)
                            }
                            bos.flush()
                            return bos.toByteArray()
                        }
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException(e.message, e)
            }

        }
    }
}

package brs.db.sql

import brs.*
import brs.db.TransactionDb
import brs.schema.tables.records.TransactionRecord
import brs.util.Convert
import org.jooq.BatchBindStep

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Optional

import brs.schema.Tables.TRANSACTION

class SqlTransactionDb(private val dp: DependencyProvider) : TransactionDb {

    override fun findTransaction(transactionId: Long): Transaction {
        return Db.useDSLContext<Transaction> { ctx ->
            try {
                val transactionRecord = ctx.selectFrom(TRANSACTION).where(TRANSACTION.ID.eq(transactionId)).fetchOne()
                return@Db.useDSLContext loadTransaction transactionRecord
            } catch (e: BurstException.ValidationException) {
                throw RuntimeException("Transaction already in database, id = $transactionId, does not pass validation!", e)
            }
        }
    }

    override fun findTransactionByFullHash(fullHash: String): Transaction {
        return Db.useDSLContext<Transaction> { ctx ->
            try {
                val transactionRecord = ctx.selectFrom(TRANSACTION).where(TRANSACTION.FULL_HASH.eq(Convert.parseHexString(fullHash))).fetchOne()
                return@Db.useDSLContext loadTransaction transactionRecord
            } catch (e: BurstException.ValidationException) {
                throw RuntimeException("Transaction already in database, full_hash = $fullHash, does not pass validation!", e)
            }
        }
    }

    override fun hasTransaction(transactionId: Long): Boolean {
        return Db.useDSLContext<Boolean> { ctx -> ctx.fetchExists(ctx.selectFrom(TRANSACTION).where(TRANSACTION.ID.eq(transactionId))) }
    }

    override fun hasTransactionByFullHash(fullHash: String): Boolean {
        return Db.useDSLContext<Boolean> { ctx -> ctx.fetchExists(ctx.selectFrom(TRANSACTION).where(TRANSACTION.FULL_HASH.eq(Convert.parseHexString(fullHash)))) }
    }

    @Throws(BurstException.ValidationException::class)
    override fun loadTransaction(tr: TransactionRecord?): Transaction? {
        if (tr == null) {
            return null
        }

        var buffer: ByteBuffer? = null
        if (tr.attachmentBytes != null) {
            buffer = ByteBuffer.wrap(tr.attachmentBytes)
            buffer!!.order(ByteOrder.LITTLE_ENDIAN)
        }

        val transactionType = TransactionType.findTransactionType(tr.type!!, tr.subtype!!)
        val builder = Transaction.Builder(dp, tr.version!!, tr.senderPublicKey,
                tr.amount!!, tr.fee!!, tr.timestamp!!, tr.deadline!!,
                transactionType!!.parseAttachment(buffer, tr.version!!))
                .referencedTransactionFullHash(tr.referencedTransactionFullhash)
                .signature(tr.signature)
                .blockId(tr.blockId!!)
                .height(tr.height!!)
                .id(tr.id!!)
                .senderId(tr.senderId!!)
                .blockTimestamp(tr.blockTimestamp!!)
                .fullHash(tr.fullHash)
        if (transactionType.hasRecipient()) {
            builder.recipientId(Optional.ofNullable(tr.recipientId).orElse(0L))
        }
        if (tr.hasMessage!!) {
            builder.message(Appendix.Message(buffer!!, tr.version!!))
        }
        if (tr.hasEncryptedMessage!!) {
            builder.encryptedMessage(Appendix.EncryptedMessage(buffer!!, tr.version!!))
        }
        if (tr.hasPublicKeyAnnouncement!!) {
            builder.publicKeyAnnouncement(Appendix.PublicKeyAnnouncement(buffer!!, tr.version!!))
        }
        if (tr.hasEncrypttoselfMessage!!) {
            builder.encryptToSelfMessage(Appendix.EncryptToSelfMessage(buffer!!, tr.version!!))
        }
        if (tr.version > 0) {
            builder.ecBlockHeight(tr.ecBlockHeight!!)
            builder.ecBlockId(Optional.ofNullable(tr.ecBlockId).orElse(0L))
        }

        return builder.build()
    }

    override fun findBlockTransactions(blockId: Long): List<Transaction> {
        return Db.useDSLContext<List<Transaction>> { ctx ->
            ctx.selectFrom(TRANSACTION)
                    .where(TRANSACTION.BLOCK_ID.eq(blockId))
                    .and(TRANSACTION.SIGNATURE.isNotNull)
                    .fetch { record ->
                        try {
                            return@ctx.selectFrom(TRANSACTION)
                                    .where(TRANSACTION.BLOCK_ID.eq(blockId))
                                    .and(TRANSACTION.SIGNATURE.isNotNull)
                                    .fetch loadTransaction record
                        } catch (e: BurstException.ValidationException) {
                            throw RuntimeException("Transaction already in database for block_id = " + Convert.toUnsignedLong(blockId) + " does not pass validation!", e)
                        }
                    }
        }
    }

    private fun getAttachmentBytes(transaction: Transaction): ByteArray? {
        var bytesLength = 0
        for (appendage in transaction.appendages) {
            bytesLength += appendage.size
        }
        if (bytesLength == 0) {
            return null
        } else {
            val buffer = ByteBuffer.allocate(bytesLength)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            for (appendage in transaction.appendages) {
                appendage.putBytes(buffer)
            }
            return buffer.array()
        }
    }

    override fun saveTransactions(transactions: List<Transaction>) {
        if (!transactions.isEmpty()) {
            Db.useDSLContext { ctx ->
                val insertBatch = ctx.batch(
                        ctx.insertInto(TRANSACTION, TRANSACTION.ID, TRANSACTION.DEADLINE,
                                TRANSACTION.SENDER_PUBLIC_KEY, TRANSACTION.RECIPIENT_ID, TRANSACTION.AMOUNT,
                                TRANSACTION.FEE, TRANSACTION.REFERENCED_TRANSACTION_FULLHASH, TRANSACTION.HEIGHT,
                                TRANSACTION.BLOCK_ID, TRANSACTION.SIGNATURE, TRANSACTION.TIMESTAMP,
                                TRANSACTION.TYPE,
                                TRANSACTION.SUBTYPE, TRANSACTION.SENDER_ID, TRANSACTION.ATTACHMENT_BYTES,
                                TRANSACTION.BLOCK_TIMESTAMP, TRANSACTION.FULL_HASH, TRANSACTION.VERSION,
                                TRANSACTION.HAS_MESSAGE, TRANSACTION.HAS_ENCRYPTED_MESSAGE,
                                TRANSACTION.HAS_PUBLIC_KEY_ANNOUNCEMENT, TRANSACTION.HAS_ENCRYPTTOSELF_MESSAGE,
                                TRANSACTION.EC_BLOCK_HEIGHT, TRANSACTION.EC_BLOCK_ID)
                                .values(null!!.toLong(), null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null))
                for (transaction in transactions) {
                    insertBatch.bind(
                            transaction.id,
                            transaction.deadline,
                            transaction.senderPublicKey,
                            if (transaction.recipientId == 0L) null else transaction.recipientId,
                            transaction.amountNQT,
                            transaction.feeNQT,
                            Convert.parseHexString(transaction.referencedTransactionFullHash),
                            transaction.height,
                            transaction.blockId,
                            transaction.signature,
                            transaction.timestamp,
                            transaction.type!!.type,
                            transaction.type!!.subtype,
                            transaction.senderId,
                            getAttachmentBytes(transaction),
                            transaction.blockTimestamp,
                            Convert.parseHexString(transaction.fullHash),
                            transaction.version,
                            transaction.message != null,
                            transaction.encryptedMessage != null,
                            transaction.publicKeyAnnouncement != null,
                            transaction.encryptToSelfMessage != null,
                            transaction.ecBlockHeight,
                            if (transaction.ecBlockId != 0L) transaction.ecBlockId else null
                    )
                }
                insertBatch.execute()
            }
        }
    }

    override fun optimize() {
        Db.optimizeTable(TRANSACTION.name)
    }
}

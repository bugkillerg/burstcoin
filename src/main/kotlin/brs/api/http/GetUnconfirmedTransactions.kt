package brs.api.http

import brs.api.http.common.JSONData
import brs.api.http.common.JSONResponses.INCORRECT_ACCOUNT
import brs.api.http.common.Parameters.ACCOUNT_PARAMETER
import brs.api.http.common.Parameters.INCLUDE_INDIRECT_PARAMETER
import brs.api.http.common.ResultFields.UNCONFIRMED_TRANSACTIONS_RESPONSE
import brs.entity.DependencyProvider
import brs.util.convert.emptyToNull
import brs.util.convert.parseAccountId
import brs.util.jetty.get
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import javax.servlet.http.HttpServletRequest

/**
 * TODO
 */
internal class GetUnconfirmedTransactions(private val dp: DependencyProvider) : APIServlet.JsonRequestHandler(
    arrayOf(APITag.TRANSACTIONS, APITag.ACCOUNTS),
    ACCOUNT_PARAMETER,
    INCLUDE_INDIRECT_PARAMETER
) {
    override fun processRequest(request: HttpServletRequest): JsonElement {
        val accountIdString = request[ACCOUNT_PARAMETER].emptyToNull()
        val includeIndirect = dp.parameterService.getIncludeIndirect(request)

        var accountId: Long = 0

        if (accountIdString != null) {
            try {
                accountId = accountIdString.parseAccountId()
            } catch (e: Exception) {
                return INCORRECT_ACCOUNT
            }
        }

        val unconfirmedTransactions = dp.unconfirmedTransactionService.all

        val transactions = JsonArray()

        for (transaction in unconfirmedTransactions) {
            if (accountId == 0L
                || accountId == transaction.senderId || accountId == transaction.recipientId
                || includeIndirect && dp.indirectIncomingService.isIndirectlyReceiving(transaction, accountId)
            ) {
                transactions.add(JSONData.unconfirmedTransaction(transaction))
            }
        }

        val response = JsonObject()

        response.add(UNCONFIRMED_TRANSACTIONS_RESPONSE, transactions)

        return response
    }
}

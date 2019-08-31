package brs.peer

import brs.Account
import brs.services.AccountService
import brs.util.Convert
import brs.util.JSON
import com.google.gson.JsonElement
import com.google.gson.JsonObject


@Deprecated("This call is no longer made by the other peers so will soon be removed.")
class GetAccountBalance @Deprecated("")
internal constructor(private val accountService: AccountService) : PeerServlet.PeerRequestHandler {

    override fun processRequest(request: JsonObject, peer: Peer): JsonElement {

        val response = JsonObject()

        val accountId = Convert.parseAccountId(JSON.getAsString(request.get(ACCOUNT_ID_PARAMETER_FIELD))!!)
        val account = accountService.getAccount(accountId)
        if (account != null) {
            response.addProperty(BALANCE_NQT_RESPONSE_FIELD, Convert.toUnsignedLong(account.balanceNQT))
        } else {
            response.addProperty(BALANCE_NQT_RESPONSE_FIELD, "0")
        }

        return response
    }

    companion object {

        internal val ACCOUNT_ID_PARAMETER_FIELD = "account"
        internal val BALANCE_NQT_RESPONSE_FIELD = "balanceNQT"
    }
}

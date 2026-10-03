package com.uptm.schoolfund.util

import android.app.Activity
import android.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import com.uptm.schoolfund.R
import com.uptm.schoolfund.data.DatabaseHelper
import com.uptm.schoolfund.model.TransactionItem

/** Asks for confirmation before deleting a transaction, then offers an Undo for a few seconds. */
object DeleteHelper {

    fun confirmDeleteTransaction(
        activity: Activity,
        db: DatabaseHelper,
        tx: TransactionItem,
        onChanged: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.dialog_delete_tx_title))
            .setMessage(activity.getString(R.string.dialog_delete_tx_msg))
            .setPositiveButton(activity.getString(R.string.btn_delete)) { _, _ ->
                db.deleteTransaction(tx.id)
                onChanged()
                Snackbar.make(
                    activity.findViewById(android.R.id.content),
                    activity.getString(R.string.msg_tx_deleted),
                    Snackbar.LENGTH_LONG
                ).setAction(activity.getString(R.string.btn_undo)) {
                    db.restoreTransaction(tx)
                    onChanged()
                }.show()
            }
            .setNegativeButton(activity.getString(R.string.btn_cancel), null)
            .show()
    }
}

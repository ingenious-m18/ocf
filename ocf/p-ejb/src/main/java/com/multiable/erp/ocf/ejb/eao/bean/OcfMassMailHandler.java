package com.multiable.erp.ocf.ejb.eao.bean;

import com.multiable.core.ejb.ds.CawDs;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.lib.StringLib;
import com.multiable.erp.core.share.lib.MacSqlTableLib;
import com.multiable.erp.root.share.interfaces.MassMailHandler;

public class OcfMassMailHandler implements MassMailHandler {

	@Override
	public void handleEmailTable(SqlTable emailTable, SqlTable massmail, SqlTable mainData) {

		String moduleName = massmail.getString(1, "moduleName");
		if (moduleName.equals("siso")) {

		}

	}

	private void sendTo(SqlTable emailTable, SqlTable massmail, SqlTable mainData, String keyTable, String keyField) {
		sendTo(emailTable, massmail, mainData, keyTable, keyField, "", "");
	}

	private void sendTo(SqlTable emailTable, SqlTable massmail, SqlTable mainData, String keyTable, String keyField,
			String footerTable, String footerField) {
		if (!mainData.isFieldExist(keyField)) {
			return;
		}
		StringBuffer sql = new StringBuffer();
		if (StringLib.isEmpty(footerTable)) {
			StringBuffer keyIds = new StringBuffer("-1");
			for (int i = 1; i <= mainData.size(); i++) {
				keyIds.append("," + mainData.getLong(i, keyField));
			}
			sql.append(" select distinct id, email from " + keyTable);
			sql.append(" where find_in_set(id, '" + keyIds + "') and email != ''");
		} else {
			for (int i = 1; i <= mainData.size(); i++) {
				long tranId = mainData.getLong(i, "id");
				long cusId = mainData.getLong(i, keyField);
				long manId = mainData.getLong(i, footerField);
				if (sql.length() > 0) {
					sql.append(" union all ");
				}
				sql.append(" select " + tranId + " as tranId, ifnull(b.email, a.email) as email ");
				sql.append(" from " + keyTable + " a left join " + footerTable + " b");
				sql.append(" on a.id = b.hId and b.id = " + manId + " and b.email != '' and b.expired = 0");
				sql.append(" where a.id = " + cusId + " and ifnull(b.email, a.email) != ''");
			}
		}
		if (sql.length() == 0) {
			return;
		}
		SqlTable myTable = CawDs.getSqlResult(sql.toString());
		if (myTable == null || myTable.size() == 0) {
			return;
		}
		String receiverType = keyTable.equals("virdept") ? "virDept" : keyTable;
		for (int i = 1; i <= mainData.size(); i++) {
			int rec = emailTable.addRow();
			emailTable.setValue(rec, "tranId", mainData.getValue(i, "id"));
			emailTable.setValue(rec, "tranCode", mainData.getValue(i, "code"));
			emailTable.setValue(rec, "tranDate", mainData.getValue(i, "tDate"));
			emailTable.setValue(rec, "receiverId", mainData.getValue(i, keyField));
			emailTable.setValue(rec, "receiverType", receiverType);
			int index = 0;
			if (StringLib.isEmpty(footerTable)) {
				index = MacSqlTableLib.seek(myTable, new String[] { "id" }, mainData, new String[] { keyField }, i);
			} else {
				index = MacSqlTableLib.seek(myTable, new String[] { "tranId" }, mainData, new String[] { "id" }, i);
			}
			if (index > 0) {
				emailTable.setValue(rec, "receiverEmail", myTable.getValue(index, "email"));
			}
		}
	}
}

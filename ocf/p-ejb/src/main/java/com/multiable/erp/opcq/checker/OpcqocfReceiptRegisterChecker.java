package com.multiable.erp.opcq.checker;

import java.util.Date;

import javax.naming.NamingException;

import com.multiable.core.ejb.checker.base.CheckRange;
import com.multiable.core.ejb.checker.base.CheckType;
import com.multiable.core.ejb.checker.base.EntityCheck;
import com.multiable.core.ejb.ds.CawDs;
import com.multiable.core.ejb.eao.curd.SeDeleteParam;
import com.multiable.core.ejb.eao.curd.SeReadParam;
import com.multiable.core.ejb.eao.curd.SeSaveParam;
import com.multiable.core.ejb.eao.localinterface.SqlEntityEAOLocal;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.dto.SeCurdKey;
import com.multiable.core.share.entity.SqlEntity;
import com.multiable.core.share.message.CheckMsg;
import com.multiable.core.share.util.JNDILocator;
import com.multiable.erp.core.share.data.TableStaticIndexAdapter;

public class OpcqocfReceiptRegisterChecker {

	@EntityCheck(type = CheckType.SAVE, range = CheckRange.AFTER, checkOrder = 201)
	public CheckMsg insertCredit(SeSaveParam param) {
		CheckMsg msg = null;

		// Step 1: Check Account with <Redemption Credit Account> = Y in [Payment Method]
		boolean use = false;
		double redepmtionAmt = 0;

		SqlEntity entity = param.getSqlEntity();
		SqlTable recregdbt = entity.getData("recregdbt");

		String idStr = "";
		for (int i = 1; i <= recregdbt.size(); i++) {
			idStr = idStr + recregdbt.getLong(i, "accId") + ",";
		}

		SqlTable accResult = null;
		if (idStr.length() > 0) {
			idStr = idStr.substring(0, idStr.length() - 1);

			String sql = "select id, `coaSetId`, redemptioncreditaccount from chacc where id in (" + idStr
					+ ") and redemptioncreditaccount = 1;";
			accResult = CawDs.getResult(sql);
		}

		if (accResult != null && accResult.size() > 0) {
			TableStaticIndexAdapter accIndex = new TableStaticIndexAdapter(accResult) {
				@Override
				public String getIndexKey() {
					return src.getValueStr(srcRow, "id");
				}
			};
			accIndex.action();

			for (int i = 1; i <= recregdbt.size(); i++) {
				int seekRow = accIndex.seek(recregdbt.getLong(i, "accId") + "");
				if (seekRow > 0) {
					use = true;
					redepmtionAmt += recregdbt.getDouble(i, "amt");
				}
			}
		}

		// Step 2: Find cus
		SqlTable mainTable = entity.getData("mainrecreg");
		Long AIId = mainTable.getLong(1, "AIId");
		SqlEntityEAOLocal entityEao = null;
		try {
			entityEao = JNDILocator.getInstance().lookupEJB("SqlEntityEAO", SqlEntityEAOLocal.class);
		} catch (NamingException e) {
			e.printStackTrace();
		}

		SeReadParam readParam = new SeReadParam("cus");
		readParam.setEntityId(AIId);
		SqlEntity cusEntity = entityEao.loadEntity(readParam);

		SqlTable ocfrcdcus = cusEntity.getData("ocfrcdcus");

		// Step 3: Check if couponcredit table has source transaction
		Long srcTranId = param.getEntityId();
		int foundrow = 0;
		for (int i = 1; i <= ocfrcdcus.size(); i++) {

			if (ocfrcdcus.getString(i, "rcdsourcetype").equals("recReg")
					&& ocfrcdcus.getLong(i, "rcdsourcetransaction") == srcTranId) {
				foundrow = i; // If found
			}

		}

		if (use) {

			// Step 3a: Yes. Update date, amount, pointspent
			if (foundrow > 0) {
				Date tDate = (Date) mainTable.getObject(1, "tDate");

				double pointspent = 0;
				pointspent = redepmtionAmt * 2;

				ocfrcdcus.setObject(foundrow, "rcddate", tDate);
				ocfrcdcus.setDouble(foundrow, "rcdsourcetransactionamount", redepmtionAmt);
				ocfrcdcus.setDouble(foundrow, "rcdpointspent", pointspent);
			} else {
				// Step 3b: No. Insert a new row
				int rec = ocfrcdcus.addRow();

				Date tDate = (Date) mainTable.getObject(1, "tDate");
				double pointspent = 0;
				pointspent = redepmtionAmt * 2;

				ocfrcdcus.setObject(rec, "rcddate", tDate);
				ocfrcdcus.setDouble(rec, "rcdsourcetransactionamount", redepmtionAmt);
				ocfrcdcus.setDouble(rec, "rcdpointspent", pointspent);
				ocfrcdcus.setString(rec, "rcdsourcetype", "recReg");
				ocfrcdcus.setLong(rec, "rcdsourcetransaction", srcTranId);
			}

		} else {
			// Step 3c: Yes. Delete the found row
			if (foundrow > 0) {
				ocfrcdcus.deleteRow(foundrow);
			}

			// Step 3d: No. No action

		}

		// Calculate total
		calculateTotalPoint(cusEntity);

		SeSaveParam saveParam = new SeSaveParam("cus");
		saveParam.setSqlEntity(cusEntity);
		entityEao.saveEntity(saveParam);

		return msg;
	}

	@EntityCheck(type = CheckType.DELETE, range = CheckRange.AFTER, checkOrder = 200)
	public CheckMsg deletePointEarnedSpent(SeDeleteParam param) {
		CheckMsg msg = null;

		// Step 1: Find cus
		SqlEntity entity = (SqlEntity) param.getJsonParam().get(SeCurdKey.SQLENTITY);
		SqlTable mainTable = entity.getData("mainrecreg");

		Long AIId = mainTable.getLong(1, "AIId");

		// Step 1b. Read customer record
		SqlEntityEAOLocal entityEao = null;
		try {
			entityEao = JNDILocator.getInstance().lookupEJB("SqlEntityEAO", SqlEntityEAOLocal.class);
		} catch (NamingException e) {
			e.printStackTrace();
		}

		SeReadParam readParam = new SeReadParam("cus");
		readParam.setEntityId(AIId);
		SqlEntity cusEntity = entityEao.loadEntity(readParam);

		// Find ocfremcust and delete row if found same transaction
		SqlTable ocfremcust = cusEntity.getData("ocfremcust");

		Long srcTranId = param.getEntityId();
		int foundrow = 0;
		for (int i = 1; i <= ocfremcust.size(); i++) {

			if (ocfremcust.getString(i, "sourceType").equals("recReg")
					&& ocfremcust.getLong(i, "sourceId") == srcTranId) {
				foundrow = i; // If found
			}

		}

		if (foundrow > 0) {
			ocfremcust.deleteRow(foundrow);
		}

		// Find ocfrcdcus and delete row if found same transaction
		SqlTable ocfrcdcus = cusEntity.getData("ocfrcdcus");

		foundrow = 0;
		for (int i = 1; i <= ocfrcdcus.size(); i++) {

			if (ocfrcdcus.getString(i, "rcdsourcetype").equals("recReg")
					&& ocfrcdcus.getLong(i, "rcdsourcetransaction") == srcTranId) {
				foundrow = i; // If found
			}
		}

		if (foundrow > 0) {
			ocfrcdcus.deleteRow(foundrow);
		}

		// Calculate total
		calculateTotalPoint(cusEntity);

		SeSaveParam saveParam = new SeSaveParam("cus");
		saveParam.setSqlEntity(cusEntity);
		entityEao.saveEntity(saveParam);

		return msg;

	}

	public void calculateTotalPoint(SqlEntity cusEntity) {
		// Calculate total
		SqlTable ocfremcust = cusEntity.getData("ocfremcust");
		SqlTable ocfrccus = cusEntity.getData("ocfrccus");
		SqlTable ocfrcdcus = cusEntity.getData("ocfrcdcus");
		SqlTable remcus = cusEntity.getData("remcus");

		// Step 1: Calculate total point earned
		double pointearned = 0d;
		for (int i = 1; i <= ocfremcust.size(); i++) {
			pointearned += ocfremcust.getDouble(i, "pointearned");
		}

		// Step 2: Calculate total point spent
		double pointspent = 0d;
		for (int i = 1; i <= ocfrccus.size(); i++) {
			pointspent += ocfrccus.getDouble(i, "rcpointspent");
		}

		for (int i = 1; i <= ocfrcdcus.size(); i++) {
			pointspent += ocfrcdcus.getDouble(i, "rcdpointspent");
		}

		// Step 3: Calculate point balance
		double balance = 0d;
		balance = pointearned - pointspent;

		remcus.setDouble(1, "pointearnedtotal", pointearned);
		remcus.setDouble(1, "pointspenttotal", pointspent);
		remcus.setDouble(1, "pointbalance", balance);
	}

}

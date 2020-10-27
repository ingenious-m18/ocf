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
import com.multiable.core.share.lib.MathLib;
import com.multiable.core.share.message.CheckMsg;
import com.multiable.core.share.util.JNDILocator;
import com.multiable.erp.core.share.data.TableStaticIndexAdapter;

public class Opcqocfremcus {

	@EntityCheck(type = CheckType.SAVE, range = CheckRange.AFTER, checkOrder = 200)
	public CheckMsg insertPointEarned(SeSaveParam param) {

		CheckMsg msg = null;

		// Step 1: Find cus
		SqlEntity entity = param.getSqlEntity();
		// SqlTable mainTable = entity.getMainData();
		SqlTable mainTable = entity.getData("maintar");

		Long cusId = mainTable.getLong(1, "cusId");

		// Step 1b. Read customer record
		SqlEntityEAOLocal entityEao = null;
		try {
			entityEao = JNDILocator.getInstance().lookupEJB("SqlEntityEAO", SqlEntityEAOLocal.class);
		} catch (NamingException e) {
			e.printStackTrace();
		}

		SeReadParam readParam = new SeReadParam("cus");
		readParam.setEntityId(cusId);
		SqlEntity cusEntity = entityEao.loadEntity(readParam);

		// Find ocfremcust
		SqlTable ocfremcust = cusEntity.getData("ocfremcust");

		// Step 2: Check if pointearn table has source transaction
		// Loop
		Long srcTranId = param.getEntityId();
		int foundrow = 0;
		for (int i = 1; i <= ocfremcust.size(); i++) {

			if (ocfremcust.getString(i, "sourceType").equals("siso")
					&& ocfremcust.getLong(i, "sourceId") == srcTranId) {
				foundrow = i; // If found
			}

		}

		if (foundrow > 0) {
			// Found, update pointearned, sourcetransactiondate

			Date tDate = (Date) mainTable.getObject(1, "tDate");

			Double pointearned = 0d;
			// Calculation

			// Find amt, depoAmt
			Double amt = mainTable.getDouble(1, "amt");
			Double depoAmt = mainTable.getDouble(1, "depoAmt");
			pointearned = MathLib.floor((amt + depoAmt) / 50) * 5;

			// pointearned = (int) (amt + depoAmt)/50 * 5

			ocfremcust.setDouble(foundrow, "pointearned", pointearned);
			ocfremcust.setObject(foundrow, "sourcetransactiondate", tDate);

		} else {
			// Not found
			int rec = ocfremcust.addRow();

			// Set date, pointearned, sourcetransactiondate, sourceType, sourceId
			Date tDate = (Date) mainTable.getObject(1, "tDate");
			Double pointearned = 0d;
			Double amt = mainTable.getDouble(1, "amt");
			Double depoAmt = mainTable.getDouble(1, "depoAmt");
			pointearned = MathLib.floor((amt + depoAmt) / 50) * 5;

			String sourceType = "siso";
			Long sourceId = mainTable.getLong(1, "id");

			ocfremcust.setObject(rec, "date", tDate);
			ocfremcust.setObject(rec, "sourcetransactiondate", tDate);
			ocfremcust.setDouble(rec, "pointearned", pointearned);
			ocfremcust.setString(rec, "sourceType", sourceType);
			ocfremcust.setLong(rec, "sourceId", sourceId);
		}

		// Step 3a: Yes

		// ocfremcust.setValue(foundrow, "", Xxx);

		// Step 3b: No

		// Save customer
		SeSaveParam saveParam = new SeSaveParam("cus");
		saveParam.setSqlEntity(cusEntity);
		entityEao.saveEntity(saveParam);

		return msg;
	}

	@EntityCheck(type = CheckType.SAVE, range = CheckRange.AFTER, checkOrder = 201)
	public CheckMsg insertCredit(SeSaveParam param) {
		CheckMsg msg = null;

		// Step 1: Check Account with <Redemption Credit Account> = Y in [Payment Method]
		boolean use = false;
		double redepmtionAmt = 0;

		SqlEntity entity = param.getSqlEntity();
		SqlTable sipaym = entity.getData("sipaym");

		String idStr = "";
		for (int i = 1; i <= sipaym.size(); i++) {
			idStr = idStr + sipaym.getLong(i, "accId") + ",";
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

			for (int i = 1; i <= sipaym.size(); i++) {
				int seekRow = accIndex.seek(sipaym.getLong(i, "accId") + "");
				if (seekRow > 0) {
					use = true;
					redepmtionAmt += sipaym.getDouble(i, "amt");
				}
			}
		}

		// Step 2: Find cus
		SqlTable mainTable = entity.getData("maintar");
		Long cusId = mainTable.getLong(1, "cusId");
		SqlEntityEAOLocal entityEao = null;
		try {
			entityEao = JNDILocator.getInstance().lookupEJB("SqlEntityEAO", SqlEntityEAOLocal.class);
		} catch (NamingException e) {
			e.printStackTrace();
		}

		SeReadParam readParam = new SeReadParam("cus");
		readParam.setEntityId(cusId);
		SqlEntity cusEntity = entityEao.loadEntity(readParam);

		SqlTable ocfrcdcus = cusEntity.getData("ocfrcdcus");

		// Step 3: Check if couponcredit table has source transaction
		Long srcTranId = param.getEntityId();
		int foundrow = 0;
		for (int i = 1; i <= ocfrcdcus.size(); i++) {

			if (ocfrcdcus.getString(i, "rcdsourcetype").equals("siso")
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
				ocfrcdcus.setString(rec, "rcdsourcetype", "siso");
				ocfrcdcus.setLong(rec, "rcdsourcetransaction", srcTranId);
			}

		} else {
			// Step 3c: Yes. Delete the found row
			if (foundrow > 0) {
				ocfrcdcus.deleteRow(foundrow);
			}

			// Step 3d: No. No action

		}
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
		SqlTable mainTable = entity.getData("maintar");

		Long cusId = mainTable.getLong(1, "cusId");

		// Step 1b. Read customer record
		SqlEntityEAOLocal entityEao = null;
		try {
			entityEao = JNDILocator.getInstance().lookupEJB("SqlEntityEAO", SqlEntityEAOLocal.class);
		} catch (NamingException e) {
			e.printStackTrace();
		}

		SeReadParam readParam = new SeReadParam("cus");
		readParam.setEntityId(cusId);
		SqlEntity cusEntity = entityEao.loadEntity(readParam);

		// Find ocfremcust and delete row if found same transaction
		SqlTable ocfremcust = cusEntity.getData("ocfremcust");

		Long srcTranId = param.getEntityId();
		int foundrow = 0;
		for (int i = 1; i <= ocfremcust.size(); i++) {

			if (ocfremcust.getString(i, "sourceType").equals("siso")
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

			if (ocfrcdcus.getString(i, "rcdsourcetype").equals("siso")
					&& ocfrcdcus.getLong(i, "rcdsourcetransaction") == srcTranId) {
				foundrow = i; // If found
			}
		}

		if (foundrow > 0) {
			ocfrcdcus.deleteRow(foundrow);
		}

		SeSaveParam saveParam = new SeSaveParam("cus");
		saveParam.setSqlEntity(cusEntity);
		entityEao.saveEntity(saveParam);

		return msg;

	}

}

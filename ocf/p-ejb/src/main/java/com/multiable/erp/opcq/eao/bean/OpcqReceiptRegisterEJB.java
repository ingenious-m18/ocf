package com.multiable.erp.opcq.eao.bean;

import java.util.List;

import javax.ejb.Local;
import javax.ejb.Stateless;

import com.multiable.core.ejb.ds.CawDs;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.entity.SqlEntity;
import com.multiable.erp.core.share.data.TableStaticIndexAdapter;
import com.multiable.opcq.share.OcfStaticVar.OcfEJB;
import com.multiable.opcq.share.interfaces.local.OpcqReceiptRegisterLocal;

@Stateless(name = OcfEJB.OpcqReceiptRegisterEJB)
@Local({ OpcqReceiptRegisterLocal.class })
public class OpcqReceiptRegisterEJB implements OpcqReceiptRegisterLocal {
	@Override
	public boolean chkBal(SqlEntity rrEntity) {

		SqlTable mainTable = rrEntity.getData("mainrecreg");

		// Step 2: Get Sales Invoice Point Spent
		SqlTable recregdbt = rrEntity.getData("recregdbt");
		double rrpointspent = 0;

		double redepmtionAmt = 0;

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
					redepmtionAmt += recregdbt.getDouble(i, "amt");
				}
			}
		}

		rrpointspent = redepmtionAmt * 2;

		// Step 3: Get Customer data
		Long rrId = mainTable.getLong(1, "id");
		Long AIId = mainTable.getLong(1, "AIId");

		if (AIId != 0) {
			String sql = " select * from ocfremcust where hId = " + AIId
					+ " and concat(`sourceType`, '_',`sourceId`) != 'recReg_" + rrId + "';";
			sql += " select * from ocfrccus where hId = " + AIId + ";";
			sql += " select * from ocfrcdcus where hId = " + AIId
					+ " and concat(`rcdsourcetype`, '_',`rcdsourcetransaction`) != 'recReg_" + rrId + "';";

			List<SqlTable> resultList = CawDs.getResults(sql);

			SqlTable ocfremcust = resultList.get(0);
			SqlTable ocfrccus = resultList.get(1);
			SqlTable ocfrcdcus = resultList.get(2);

			// Step 4: Calculate Balance

			// Customer pointearned
			double pointearned = 0d;
			for (int i = 1; i <= ocfremcust.size(); i++) {
				pointearned += ocfremcust.getDouble(i, "pointearned");
			}

			// Customer pointspent
			double pointspent = 0d;
			for (int i = 1; i <= ocfrccus.size(); i++) {
				pointspent += ocfrccus.getDouble(i, "rcpointspent");
			}

			for (int i = 1; i <= ocfrcdcus.size(); i++) {
				pointspent += ocfrcdcus.getDouble(i, "rcdpointspent");
			}

			// Step 5: Check balance
			double balance = 0d;
			balance = pointearned - pointspent - rrpointspent;

			if (balance >= 0) {
				return true;
			} else {
				return false;
			}

		}

		return true;
	}
}

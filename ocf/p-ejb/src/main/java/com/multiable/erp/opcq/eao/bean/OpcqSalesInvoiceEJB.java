package com.multiable.erp.opcq.eao.bean;

import java.util.List;

import javax.ejb.Local;
import javax.ejb.Stateless;

import com.multiable.core.ejb.ds.CawDs;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.entity.SqlEntity;
import com.multiable.core.share.lib.MathLib;
import com.multiable.erp.core.share.data.TableStaticIndexAdapter;
import com.multiable.opcq.share.OcfStaticVar.OcfEJB;
import com.multiable.opcq.share.interfaces.local.OpcqSalesInvoiceLocal;

@Stateless(name = OcfEJB.OpcqSalesInvoiceEJB)
@Local({ OpcqSalesInvoiceLocal.class })
public class OpcqSalesInvoiceEJB implements OpcqSalesInvoiceLocal {
	@Override
	public boolean chkBal(SqlEntity siEntity) {

		SqlTable mainTable = siEntity.getData("maintar");

		// Step 1: Get Sales Invoice Point Earned
		double sipointearned = 0;
		double amt = mainTable.getDouble(1, "amt");
		double depoAmt = mainTable.getDouble(1, "depoAmt");
		sipointearned = MathLib.floor((amt + depoAmt) / 50) * 5;

		// Step 2: Get Sales Invoice Point Spent
		SqlTable sipaym = siEntity.getData("sipaym");
		double sipointspent = 0;

		double redepmtionAmt = 0;

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
					redepmtionAmt += sipaym.getDouble(i, "amt");
				}
			}
		}

		sipointspent = redepmtionAmt * 2;

		// Step 3: Get Customer data
		Long siId = mainTable.getLong(1, "id");
		Long cusId = mainTable.getLong(1, "cusId");

		if (cusId != 0) {
			String sql = " select * from ocfremcust where hId = " + cusId
					+ " and concat(`sourceType`, '_',`sourceId`) != 'siso_" + siId + "';";
			sql += " select * from ocfrccus where hId = " + cusId + ";";
			sql += " select * from ocfrcdcus where hId = " + cusId
					+ " and concat(`rcdsourcetype`, '_',`rcdsourcetransaction`) != 'siso_" + siId + "';";

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
			balance = pointearned - pointspent + sipointearned - sipointspent;

			if (balance >= 0) {
				return true;
			} else {
				return false;
			}

		}

		return true;
	}
}

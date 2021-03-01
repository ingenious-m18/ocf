package com.multiable.erp.ocf.ejb.checker;

import com.multiable.core.ejb.checker.base.CheckRange;
import com.multiable.core.ejb.checker.base.CheckType;
import com.multiable.core.ejb.checker.base.EntityCheck;
import com.multiable.core.ejb.eao.curd.SeSaveParam;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.entity.SqlEntity;
import com.multiable.core.share.message.CheckMsg;

public class OcfCusChecker {

	@EntityCheck(type = CheckType.SAVE, range = CheckRange.BEFORE, checkOrder = 200)
	public CheckMsg updateTotalPoint(SeSaveParam param) {

		CheckMsg msg = null;

		SqlEntity entity = param.getSqlEntity();

		SqlTable ocfremcust = entity.getData("ocfremcust");
		SqlTable ocfrccus = entity.getData("ocfrccus");
		SqlTable ocfrcdcus = entity.getData("ocfrcdcus");
		SqlTable remcus = entity.getData("remcus");

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

		return msg;
	}

}

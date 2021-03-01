package com.multiable.erp.ocf.ejb.checker;

import com.multiable.core.ejb.checker.base.CheckRange;
import com.multiable.core.ejb.checker.base.CheckType;
import com.multiable.core.ejb.checker.base.EntityCheck;
import com.multiable.core.ejb.ds.CawDs;
import com.multiable.core.ejb.eao.curd.SeDeleteParam;
import com.multiable.core.share.data.SqlTable;
import com.multiable.core.share.message.CheckMsg;
import com.multiable.core.share.message.CheckMsgLib;
import com.multiable.erp.ocf.share.message.OcfTermsError;

public class OcfTermsChecker {

	@EntityCheck(type = CheckType.DELETE, range = CheckRange.BEFORE, checkOrder = 50)
	public CheckMsg checkUsedTerms(SeDeleteParam param) {

		StringBuilder sb = new StringBuilder();
		sb.append(" select 1 from cus where ocfTerms = '" + param.getEntityId() + "'");
		sb.append(" union");
		sb.append(" select 1 from maindn where ocfTerms = '" + param.getEntityId() + "'");
		sb.append(" union");
		sb.append(" select 1 from maintar where ocfTerms = '" + param.getEntityId() + "'");
		sb.append(";");

		SqlTable srResult = CawDs.getResult(sb.toString());
		if (srResult != null && srResult.size() > 0) {
			CheckMsg msg = CheckMsgLib.createMsg(OcfTermsError.FAIL_USED_TERMS, "");
			return msg;
		}

		return null;
	}

}

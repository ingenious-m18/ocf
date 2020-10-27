package com.multiable.erp.opcq.ireport.provider.cardsizelarge;

import com.multiable.core.ejb.ds.MacQuery;
import com.multiable.core.share.data.ireport.CawReportDataSet;
import com.multiable.erp.core.ejb.ireport.MacModuleProvider;
import com.multiable.erp.core.share.util.MacReportUtil;
import com.multiable.opcq.share.data.ireport.cardsize.CardSizeJrDto;

public class CardSizeLargeProvider extends MacModuleProvider {
	@Override
	public void initReportStru(CawReportDataSet reData) {
		reData.setQuery(0);
		int i = -1;
		i = MacReportUtil.setAlias(reData, i, "maindn", "maindn");
	}

	@Override
	public void adjustData(CawReportDataSet reData) {
		super.adjustData(reData);
		CardSizeJrDto jrDto = (CardSizeJrDto) getReportDto();
		handleHtmlField(reData, jrDto);

	}

	@Override
	public CawReportDataSet genIdsData() {
		super.genIdsData();

		CardSizeJrDto jrDto = (CardSizeJrDto) getReportDto();
		String sql = "{CALL ocf_prtCardSizeLarge('" + jrDto.getMainIdString() + "')}";
		MacQuery query = new MacQuery();
		query.setQuery(sql);
		return fillAndAdjustData(query);
	}
}

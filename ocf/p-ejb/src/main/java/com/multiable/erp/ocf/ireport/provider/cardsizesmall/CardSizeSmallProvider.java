package com.multiable.erp.ocf.ireport.provider.cardsizesmall;

import com.multiable.core.ejb.ds.MacQuery;
import com.multiable.core.share.data.ireport.CawReportDataSet;
import com.multiable.erp.core.ejb.ireport.MacModuleProvider;
import com.multiable.erp.core.share.util.MacReportUtil;
import com.multiable.erp.ocf.share.data.ireport.cardsize.CardSizeJrDto;

public class CardSizeSmallProvider extends MacModuleProvider {
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
		String sql = "{CALL ocf_prtCardSizeSmall('" + jrDto.getMainIdString() + "')}";
		MacQuery query = new MacQuery();
		query.setQuery(sql);
		return fillAndAdjustData(query);
	}
}

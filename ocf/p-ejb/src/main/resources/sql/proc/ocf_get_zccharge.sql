drop procedure if exists ocf_get_zccharge;
#call ocf_get_zccharge(1, 4, '2021-01-15', '01');
DELIMITER $$

CREATE PROCEDURE ocf_get_zccharge(
	IN beId bigint,
	IN transAccId bigint,
	IN asAt date,
	IN zipCode varchar(10)
)
BEGIN
	drop temporary table if exists temp_pro;
	drop temporary table if exists t_accinfo;
	drop temporary table if exists t_taxinfo;
	drop temporary table if exists t_zipcode;



	-- Get zip code charge
	create temporary table t_zipcode
	select a.postalcode, a.deliverychargeperdistance
	from ocfpostalcodedeliverycharget as a, ocfpostalcodedeliverycharge as b
	where a.hId = b.id and b.id != 0
	and b.beId = beId
	and a.postalcode = zipCode;
	

	-- Get transport account
	create temporary table t_accinfo
	select a.id, a.`desc`
	from chacc a 
	where a.id != 0 
		and a.id in (transAccId);
	
	create temporary table t_taxinfo
	select a.id as accId, b.taxCodeId, d.taxRate
	from chacc a
		inner join actaxsetting b on a.id = b.hId
		inner join taxcode c on b.taxCodeId = c.id
		inner join taxcodet d on c.id = d.hId
	where a.id in (transAccId)
		and a.taxNeed = 1
		and b.vatBeId = beId
		and b.effDate <= asAt
		and b.expDate >= asAt
		and d.effDate <= asAt
		and d.expDate >= asAt;
	
	-- Table 1: Get charge table
	select a.id as accId, a.`desc`
		, ifnull(t.taxCodeId, 0) as taxCodeId, ifnull(t.taxRate, 0) as taxRate
		, b.deliverychargeperdistance as zcCharge
	from t_accinfo a
		left join t_taxinfo t on a.id = t.accId
		, t_zipcode b
	where a.id = transAccId;


	drop temporary table if exists temp_pro;
	drop temporary table if exists t_accinfo;
	drop temporary table if exists t_taxinfo;
	drop temporary table if exists t_zipcode;

END$$

DELIMITER ;
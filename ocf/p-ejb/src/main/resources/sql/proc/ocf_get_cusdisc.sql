drop procedure if exists ocf_get_cusdisc;
#call ocf_get_cusdisc(1, 1, '(`proId`,`unitId`,`qty`) values (3.0,3.0,1.0),(1.0,1.0,1.0)', 4, '2021-01-15', '');
DELIMITER $$

CREATE PROCEDURE ocf_get_cusdisc(
	IN beId bigint,
	IN cusId bigint,
	IN sxml longtext,
	IN transAccId bigint,
	IN asAt date,
	IN zipCode varchar(10)
)
BEGIN
	drop temporary table if exists temp_pro;
	drop temporary table if exists t_accinfo;
	drop temporary table if exists t_taxinfo;
	drop temporary table if exists t_zipcode;


	create temporary table temp_pro
	select hId as proId, unitId, cast(0 as decimal(15,4)) as qty
	from price
	where 1 = 2;
	create index i_rate_curId on temp_pro(proId, unitId);
	
	if sxml is not null and sxml != '' then
		set @mysql = CONCAT('insert into temp_pro', sxml);
		PREPARE STMT FROM @mysql;
		EXECUTE STMT;
		DEALLOCATE PREPARE STMT;
	end if;

	-- Table 1: Get customer discount
	select id, transportcharge, invoicediscount
	from cus where id = cusId;


	-- Table 2: Check if product is add on item
	select b.proId, b.unitId, b.qty, a.ocfAddOnItem
	from pro a
		, temp_pro b
	where a.id = b.proId;


	-- Get zip code charge
	create temporary table t_zipcode
	select a.postalcode, a.deliverychargeperdistance
	from ocfpostalcodedeliverycharget as a, ocfpostalcodedeliverycharge as b
	where a.hId = b.id and b.id != 0
	and b.beId = beId
	and a.postalcode = zipCode;
	
	-- Table 3: zip code
	select * from t_zipcode;


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
	
	-- Table 4: Get charge table
	select a.id as accId, a.`desc`, ifnull(t.taxCodeId, 0) as taxCodeId, ifnull(t.taxRate, 0) as taxRate
	from t_accinfo a
		left join t_taxinfo t on a.id = t.accId
	where a.id = transAccId;


	drop temporary table if exists temp_pro;
	drop temporary table if exists t_accinfo;
	drop temporary table if exists t_taxinfo;
	drop temporary table if exists t_zipcode;

END$$

DELIMITER ;
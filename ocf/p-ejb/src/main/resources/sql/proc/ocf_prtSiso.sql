drop procedure if exists ocf_prtSiso;

DELIMITER $$
#CALL ocf_prtSiso('1');
CREATE PROCEDURE ocf_prtSiso(
	IN siId longtext
)
BEGIN
	DROP TEMPORARY TABLE IF EXISTS t_prtsiso_mod;
	DROP TEMPORARY TABLE IF EXISTS t_cus;

	CREATE TEMPORARY TABLE t_prtsiso_mod
	SELECT a.id, a.beId, a.cusId, a.staffId, a.cnDeptId, a.virDeptId, a.curId, a.code, a.status, a.createUid, a.lastApproveUid as apvUid
	FROM maintar AS a
	WHERE find_in_set(a.id, siId) ; 

	-- Query 0 maintar 
	SELECT a.id AS siId, a.*
        , ifnull(b.man, '') as man
        , ifnull(b.telCountry, c.telCountry) as telCountry
        , ifnull(b.telArea, c.telArea) as telArea
        , ifnull(b.tel, c.tel) as tel
        , ifnull(b.faxCountry, c.faxCountry) as faxCountry
        , ifnull(b.faxArea, c.faxArea) as faxArea
        , ifnull(b.fax, c.fax) as fax
        , ifnull(b.i18nField, '') as manI18nField
        , t.code AS jlTypeCode, t.`desc` AS jlTypeDesc
        , v.code AS virDeptCode, v.`desc` AS virDeptDesc
    FROM maintar AS a LEFT JOIN cust AS b ON a.manId = b.id and b.id > 0, cus AS c, doctype AS t, virdept AS v
    WHERE a.id IN (SELECT x.id FROM t_prtsiso_mod x) AND
        a.cusId = c.id and 
        a.jlTypeId = t.id and 
        a.virDeptId = v.id
   ORDER BY a.code;
	
	-- Query 1 art
	select b.hId as siId, b.*, c.code AS proCode, ifnull(n.code, '') as dnCode
	from art AS b left join maindn n on b.sourceType = 'dn' and b.sourceId = n.id
		, pro AS c
	where b.hId IN (SELECT x.id FROM t_prtsiso_mod x)
		and b.proId = c.id;

	-- Query 2 sidisc
    SELECT a.hId AS siId, a.*
    FROM sidisc AS a
    WHERE a.hId IN (SELECT x.id FROM t_prtsiso_mod x)
    ORDER BY a.hId, a.c_d, a.itemNo;
   
	-- Query 3 remtar
    SELECT a.hId AS siId, a.*, a.tel as shiptel, a.fax as shipfax
    FROM (SELECT a.*, x.cusId
		FROM remtar AS a, t_prtsiso_mod x
		WHERE a.hId = x.id
        ) AS a;
       
	-- Query 4 cus
	CREATE TEMPORARY TABLE t_cus
	SELECT b.*
	FROM cus AS b
	WHERE b.id IN (SELECT x.cusId FROM t_prtsiso_mod x);

	SELECT * from t_cus;


	-- Query 5 ocfremcust
	SELECT b.*
	FROM ocfremcust AS b
	WHERE b.hId IN (SELECT x.id FROM t_cus x)
		AND b.sourceType = 'siso'
		AND b.`sourceId` IN (SELECT y.id FROM t_prtsiso_mod y);

	DROP TEMPORARY TABLE IF EXISTS t_prtsiso_mod;
	DROP TEMPORARY TABLE IF EXISTS t_cus;

END$$

DELIMITER ;
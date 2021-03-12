drop procedure if exists ocf_prtDn;

DELIMITER $$
#CALL ocf_prtDn('1');
CREATE PROCEDURE ocf_prtDn(
	IN dnid longtext
)
BEGIN
	DROP TEMPORARY TABLE IF EXISTS t_prtdn_mod;
	DROP TEMPORARY TABLE IF EXISTS t_prtdnt;

	CREATE TEMPORARY TABLE t_prtdn_mod
	SELECT a.id, a.beId, a.cusId, a.curId, a.staffId, a.cnDeptId, a.virDeptId, a.locId, a.manId, a.code, a.status, a.createUid, a.lastApproveUid as apvUid
	FROM maindn AS a
	WHERE find_in_set(a.id, dnid) ; 

	-- Query 0 maindn 
	select a.id as dnId, a.*
	from maindn a
	where a.id IN (SELECT x.id FROM t_prtdn_mod x);
	
	set @row_number := 0;
	
	-- Query 1 dnt
	CREATE TEMPORARY TABLE t_prtdnt
	select b.hId as dnId, b.*, c.code AS proCode
		, @row_number := CASE WHEN @dnId = b.hId THEN @row_number + 1
			ELSE 1 END AS dntGpNum
		, @dnId := b.hId dntDnId
	from dnt b
		, pro AS c
	where b.hId IN (SELECT x.id FROM t_prtdn_mod x)
		and b.proId = c.id;
	
	select * from t_prtdnt
	where dntGpNum <= 12
	order by dnId, itemNo;

	-- Query 2: remdn
	select a.hId as dnId, a.*
	from remdn a
	where a.hId IN (SELECT x.id FROM t_prtdn_mod x);

	-- Query 3 staff
	SELECT b.*, c.`desc` as position, c.i18nField as positioni18nField
	FROM staff AS b, position AS c
	WHERE b.id IN (SELECT x.staffId FROM t_prtdn_mod x) and b.position = c.id;


	DROP TEMPORARY TABLE IF EXISTS t_prtdn_mod;
	
END$$

DELIMITER ;
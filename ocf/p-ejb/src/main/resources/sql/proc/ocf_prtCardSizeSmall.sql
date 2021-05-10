drop procedure if exists ocf_prtCardSizeSmall;

DELIMITER $$
#CALL ocf_prtCardSizeSmall('1');
CREATE PROCEDURE ocf_prtCardSizeSmall(
	IN dnid longtext
)
BEGIN
		
	DROP TEMPORARY TABLE if exists t_prtdn_mod;
	
	CREATE TEMPORARY TABLE t_prtdn_mod
	SELECT a.id
	FROM maindn AS a
	WHERE find_in_set(a.id, dnid);

	-- Query 0 maindn
	select a.id as dnId, a.*
	from maindn a
	where a.id IN (SELECT id FROM t_prtdn_mod);
		
	-- Query 1 remdn
	select b.hId as dnId, b.*
	from remdn b
	where b.hId IN (SELECT id FROM t_prtdn_mod);
	
	DROP TEMPORARY TABLE if exists t_prtdn_mod;
		
END$$

DELIMITER ;
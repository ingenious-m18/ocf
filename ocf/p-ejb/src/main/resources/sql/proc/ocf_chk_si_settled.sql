DROP PROCEDURE IF EXISTS ocf_chk_si_settled;
#call ocf_chk_si_settled(1, 0, '1,2')
DELIMITER $$
CREATE PROCEDURE ocf_chk_si_settled(
	IN beId bigint,
	IN rrId bigint,
	IN siIdStr longtext
	)  
BEGIN
	
	drop temporary table if exists t_si;
    drop temporary table if exists t_rr;
    
    
    create temporary table t_si
	select id, tDate, amt, depoAmt, ocfEntitledToPts
		, amt as unsettledAmt
	from maintar
	where id != 0
		and find_in_set(id, siIdStr);

	create temporary table t_rr
	select sTranId as siId, sum(amt) as amt
	from recregt a
	where a.`hId` != rrId
		and a.sTranType = 'siso'
		and find_in_set(a.`sTranId`, siIdStr)
	group by a.`sTranId`;
	
	update t_si a, t_rr b
	set a.unsettledAmt = a.amt - b.amt
	where a.id = b.siId;

	select * from t_si;
        
    drop temporary table if exists t_si;
    drop temporary table if exists t_rr;
   
END$$

DELIMITER ;
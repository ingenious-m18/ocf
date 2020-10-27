drop procedure if exists ocf_prtCardSizeLarge;

DELIMITER $$
#CALL ocf_prtCardSizeLarge('1');
CREATE PROCEDURE ocf_prtCardSizeLarge(
	IN dnid longtext
)
BEGIN
	select a.id, a.code, b.ocfrecipient, b.ocfsender, b.msgcontent
	from maindn a, remdn b
	where a.id = b.hId
		and a.id = dnid;
END$$

DELIMITER ;
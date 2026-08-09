package com.forensics.platform.repository;

import com.forensics.platform.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {
	List<Device> findByInvestigationCaseId(Long caseId);
	void deleteByInvestigationCaseId(Long caseId);

	@Query(value = """
		select coalesce(nullif(trim(operating_system), ''), 'Unknown') as label, count(*) as value
		from device
		group by coalesce(nullif(trim(operating_system), ''), 'Unknown')
		order by value desc, label asc
	""", nativeQuery = true)
	List<Object[]> countDevicesByOperatingSystem();
	boolean existsByImeiIgnoreCase(String imei);
	Optional<Device> findByImei(String imei);
}

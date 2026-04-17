package com.busymumkitchen.repository;
import com.busymumkitchen.model.DailyOrderSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;
@Repository
public interface DailyOrderSequenceRepository extends JpaRepository<DailyOrderSequence, LocalDate> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DailyOrderSequence s WHERE s.orderDate = :date")
    Optional<DailyOrderSequence> findByDateForUpdate(@Param("date") LocalDate date);
}

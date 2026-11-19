package com.project.simulation;

import com.project.alert.AlertService;
import com.project.dbms.repository.AlertRepository;
import com.project.dbms.repository.ReorderRequestRepository;
import com.project.os.ConcurrencyMode;
import com.project.os.ExecutionResult;
import com.project.os.OsEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("SIMULATION")
class ScenarioRunnerTest {

    private OsEngine osEngine;
    private ConcurrencyLabService labService;
    private AlertRepository alertRepository;
    private ReorderRequestRepository reorderRepository;
    private AlertService alertService;
    private ScenarioRunnerService runnerService;

    @BeforeEach
    void setUp() {
        osEngine = mock(OsEngine.class);
        labService = mock(ConcurrencyLabService.class);
        alertRepository = mock(AlertRepository.class);
        reorderRepository = mock(ReorderRequestRepository.class);
        alertService = mock(AlertService.class);

        when(osEngine.processStockEventSync(any())).thenReturn(
                ExecutionResult.builder().success(true).status("COMPLETED").build()
        );

        runnerService = new ScenarioRunnerService(
                osEngine, labService, alertRepository, reorderRepository, alertService
        );
    }

    @Test
    @DisplayName("List available scenarios returns all 5 predefined scenarios")
    void testListScenarios() {
        List<ScenarioStatus> scenarios = runnerService.listAvailableScenarios();
        assertEquals(5, scenarios.size());
        assertTrue(scenarios.stream().anyMatch(s -> "flash_sale".equals(s.getScenarioId())));
        assertTrue(scenarios.stream().anyMatch(s -> "stock_out_cascade".equals(s.getScenarioId())));
        assertTrue(scenarios.stream().anyMatch(s -> "concurrency_stress".equals(s.getScenarioId())));
        assertTrue(scenarios.stream().anyMatch(s -> "supplier_delay".equals(s.getScenarioId())));
        assertTrue(scenarios.stream().anyMatch(s -> "mixed_day".equals(s.getScenarioId())));
    }

    @Test
    @DisplayName("Trigger scenario transitions state to RUNNING then COMPLETED")
    void testRunScenario() throws InterruptedException {
        ScenarioStatus status = runnerService.runScenario("flash_sale");
        assertNotNull(status);
        assertEquals("flash_sale", status.getScenarioId());

        // Wait briefly for background execution to complete
        Thread.sleep(800);
        ScenarioStatus completed = runnerService.getScenarioStatus("flash_sale");
        assertNotNull(completed);
        assertEquals("COMPLETED", completed.getStatus());
        assertEquals(1000, completed.getTotalEvents());
        verify(osEngine, times(1000)).processStockEventSync(any());
    }
}

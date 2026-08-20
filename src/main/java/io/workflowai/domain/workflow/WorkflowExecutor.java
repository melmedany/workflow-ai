package io.workflowai.domain.workflow;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Owns the compiled LangGraph4j graph for one agent and adapts it. Nothing about LangGraph4j leaks past this class.
 */
public class WorkflowExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutor.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 300;

    private final CompiledGraph<WorkflowState> graph;

    WorkflowExecutor(CompiledGraph<WorkflowState> graph) {
        this.graph = graph;
    }

    public WorkflowExecutionResult execute(Map<String, Object> initialState) {
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> graph.invoke(initialState), executorService);

        try {
            future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return WorkflowExecutionResult.completed();
        } catch (TimeoutException ex) {
            future.cancel(true);
            return WorkflowExecutionResult.timedOut("Workflow execution timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds");
        } catch (InterruptedException ex) {
            future.cancel(true);
            return WorkflowExecutionResult.failed("Workflow execution interrupted", ex);
        } catch (ExecutionException ex) {
            log.warn("Workflow execution failed: {}", ex.getMessage(), ex);
            return WorkflowExecutionResult.failed("Unexpected workflow failure: " + ex.getMessage(), ex);
        } finally {
            executorService.shutdownNow();
        }
    }

    public String diagram(String title) {
        return graph.getGraph(GraphRepresentation.Type.MERMAID, title).content();
    }
}
package io.workflowai.adapter.out.runtime.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

import java.util.Map;

/**
 * Extends LangGraph4j's {@link AgentState}. Every node translates this into the application's
 * framework-free {@link io.workflowai.application.execution.WorkflowState} on the way in and passes
 * the returned partial-state map straight through on the way out — see {@link LangGraph4jWorkflowExecutorFactory}.
 */
class LangGraph4jState extends AgentState {

    static final AgentStateFactory<LangGraph4jState> SCHEMA = LangGraph4jState::new;

    LangGraph4jState(Map<String, Object> initData) {
        super(initData);
    }
}
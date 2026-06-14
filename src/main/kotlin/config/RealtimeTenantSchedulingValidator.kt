package config

internal object RealtimeTenantSchedulingValidator {
    fun validate(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        if (scheduling.tenantCount < 1) {
            context.addError("realtime.scheduling.tenantCount", scheduling.tenantCount, "租户数量必须大于等于 1")
        }
        validateTenantQuota(scheduling, context)
        validateTenantWeights(scheduling, context)
        enumValue(
            context,
            "realtime.scheduling.tenantFairnessPolicy",
            scheduling.tenantFairnessPolicy,
            RealtimeTenantFairnessPolicy.valuesForConfig(),
            "租户公平策略",
        )
        enumValue(
            context,
            "realtime.scheduling.tenantSchedulingPolicy",
            scheduling.tenantSchedulingPolicy,
            TenantSchedulingPolicy.valuesForConfig(),
            "租户调度策略",
        )
        if (scheduling.tenantBurstAllowance < 0) {
            context.addError(
                "realtime.scheduling.tenantBurstAllowance",
                scheduling.tenantBurstAllowance,
                "租户突发额度不能为负数",
            )
        }
        nonNegative(
            context,
            "realtime.scheduling.tenantSlaPenaltyWeight",
            scheduling.tenantSlaPenaltyWeight,
            "租户 SLA 惩罚权重不能为负数",
        )
        validateTenantCostBudget(scheduling, context)
    }

    private fun validateTenantQuota(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        if (scheduling.tenantQuota.isNotEmpty() && scheduling.tenantQuota.size != scheduling.tenantCount) {
            context.addError(
                "realtime.scheduling.tenantQuota",
                scheduling.tenantQuota.joinToString(","),
                "租户配额数量必须等于 tenantCount",
            )
        }
        scheduling.tenantQuota.forEachIndexed { index, quota ->
            if (quota < 0) {
                context.addError("realtime.scheduling.tenantQuota[$index]", quota, "租户配额不能为负数")
            }
        }
    }

    private fun validateTenantWeights(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        if (scheduling.tenantWeights.isNotEmpty() && scheduling.tenantWeights.size != scheduling.tenantCount) {
            context.addError(
                "realtime.scheduling.tenantWeights",
                scheduling.tenantWeights.joinToString(","),
                "租户权重数量必须等于 tenantCount",
            )
        }
        scheduling.tenantWeights.forEachIndexed { index, weight ->
            if (weight <= 0.0) {
                context.addError("realtime.scheduling.tenantWeights[$index]", weight, "租户权重必须大于 0")
            }
        }
    }

    private fun validateTenantCostBudget(
        scheduling: RealtimeSchedulingConfig,
        context: RealtimeValidationContext,
    ) {
        if (scheduling.tenantCostBudget.isNotEmpty() && scheduling.tenantCostBudget.size != scheduling.tenantCount) {
            context.addError(
                "realtime.scheduling.tenantCostBudget",
                scheduling.tenantCostBudget.joinToString(","),
                "租户成本预算数量必须等于 tenantCount",
            )
        }
        scheduling.tenantCostBudget.forEachIndexed { index, budget ->
            if (budget < 0.0) {
                context.addError("realtime.scheduling.tenantCostBudget[$index]", budget, "租户成本预算不能为负数")
            }
        }
    }
}

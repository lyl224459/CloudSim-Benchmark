package cli

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CliParserBranchTest {
    @Test
    fun `run command accepts inline mode and normalizes aliases`() {
        val batch = CliParser(arrayOf("run", "--mode=bm", "--tasks=5,10")).parse()
        val realtime = CliParser(arrayOf("run", "--mode", "r", "--sequential")).parse()

        assertThat(batch)
            .isEqualTo(CliParser.RunCommand(mode = "batch-multi", taskCounts = listOf(5, 10)))
        assertThat(realtime)
            .isEqualTo(CliParser.RunCommand(mode = "realtime", useCoroutines = false))
    }

    @Test
    fun `flag options reject inline values`() {
        val error =
            assertThrows<IllegalArgumentException> {
                CliParser(arrayOf("run", "--dry-run=true")).parse()
            }

        assertThat(error.message).contains("未知参数", "--dry-run=true")
    }

    @Test
    fun `list and config commands report missing required options`() {
        assertThat(parseError("list", "algorithms")).contains("list algorithms 需要 --mode")
        assertThat(parseError("list", "profiles")).contains("list profiles 需要 --config")
        assertThat(parseError("config", "validate")).contains("config validate 需要 --config")
        assertThat(parseError("config", "print")).contains("config print 需要 --config")
    }

    @Test
    fun `list and config commands reject unknown parameters and positional arguments`() {
        assertThat(parseError("list", "algorithms", "--mode", "batch", "--bad")).contains("未知参数: --bad")
        assertThat(parseError("list", "profiles", "experiment.toml")).contains("意外的位置参数: experiment.toml")
        assertThat(parseError("config", "print", "--config", "config.toml", "profile")).contains(
            "意外的位置参数: profile",
        )
    }

    @Test
    fun `run command keeps numeric error keywords for invalid values`() {
        assertThat(parseError("run", "--tasks", "1,nope")).contains("无效的任务数")
        assertThat(parseError("run", "--runs", "0")).contains("--runs", "大于 0")
        assertThat(parseError("run", "--seed", "abc")).contains("--seed", "必须是整数")
        assertThat(parseError("run", "--concurrency", "-1")).contains("--concurrency", "大于 0")
    }

    @Test
    fun `unknown command and subcommands keep supported command hints`() {
        assertThat(parseError("unknown")).contains("未知命令", "run, list, config")
        assertThat(parseError("list", "unknown")).contains("未知 list 子命令")
        assertThat(parseError("config", "unknown")).contains("未知 config 子命令")
    }

    private fun parseError(vararg args: String): String {
        val error = assertThrows<IllegalArgumentException> { CliParser(arrayOf(*args)).parse() }
        return error.message.orEmpty()
    }
}

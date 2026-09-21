package com.trionsandroid.app.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

private const val README_ASSET = "welcome_readme.md"

// Can't be dismissed without tapping OK.
private val MustTapOk = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)

@Composable
fun WelcomeDialog(onOk: () -> Unit) {
    val context = LocalContext.current
    val blocks = remember {
        val text = runCatching { context.assets.open(README_ASSET).bufferedReader().use { it.readText() } }
            .getOrDefault("Welcome to Trio Follower.")
        parseReadme(text)
    }
    AlertDialog(
        onDismissRequest = {},
        properties = MustTapOk,
        title = { Text("Welcome") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                blocks.forEach { block -> ReadmeBlockView(block) }
            }
        },
        confirmButton = { TextButton(onClick = onOk) { Text("OK") } },
    )
}

@Composable
fun ConnectNightscoutDialog(onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = MustTapOk,
        title = { Text("Connect to Nightscout") },
        text = {
            Text("To get started, enter your Nightscout URL and a valid access token. Tap Test connection to check that they work.")
        },
        confirmButton = { TextButton(onClick = onOk) { Text("OK") } },
    )
}

// Minimal markdown renderer for the README (headings, bullets, paragraphs, bold, links, code).
// The Installation and Screenshots sections are skipped.

private sealed interface ReadmeBlock {
    data class Heading(val level: Int, val text: String) : ReadmeBlock
    data class Bullet(val text: String) : ReadmeBlock
    data class Paragraph(val text: String) : ReadmeBlock
}

private fun parseReadme(markdown: String): List<ReadmeBlock> {
    val blocks = mutableListOf<ReadmeBlock>()
    val paragraph = StringBuilder()
    var bullet: StringBuilder? = null
    var skipping = false

    fun flush() {
        if (paragraph.isNotEmpty()) blocks += ReadmeBlock.Paragraph(paragraph.toString().trim())
        bullet?.let { blocks += ReadmeBlock.Bullet(it.toString().trim()) }
        paragraph.clear()
        bullet = null
    }

    for (raw in markdown.lines()) {
        val line = raw.trimEnd()
        when {
            line.startsWith("#") -> {
                flush()
                val level = line.takeWhile { it == '#' }.length
                val title = line.dropWhile { it == '#' }.trim()
                skipping = level >= 2 && (title.equals("Installation", ignoreCase = true) || title.equals("Screenshots", ignoreCase = true))
                if (!skipping) blocks += ReadmeBlock.Heading(level, title)
            }
            skipping -> Unit
            line.isBlank() -> flush()
            line.startsWith("- ") -> {
                flush()
                bullet = StringBuilder(line.removePrefix("- "))
            }
            raw.startsWith(" ") && bullet != null -> bullet!!.append(' ').append(line.trim())
            else -> {
                bullet?.let { blocks += ReadmeBlock.Bullet(it.toString().trim()); bullet = null }
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line.trim())
            }
        }
    }
    flush()
    return blocks
}

private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
private val CODE = Regex("`([^`]*)`")
private val BOLD = Regex("""\*\*(.+?)\*\*""")

private fun inline(text: String): AnnotatedString {
    val plain = text.replace(LINK) { it.groupValues[1] }.replace(CODE) { it.groupValues[1] }
    return buildAnnotatedString {
        var last = 0
        for (match in BOLD.findAll(plain)) {
            append(plain.substring(last, match.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
            last = match.range.last + 1
        }
        append(plain.substring(last))
    }
}

@Composable
private fun ReadmeBlockView(block: ReadmeBlock) {
    when (block) {
        is ReadmeBlock.Heading -> {
            Spacer(Modifier.height(if (block.level == 1) 0.dp else 12.dp))
            Text(
                text = inline(block.text),
                style = if (block.level == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        is ReadmeBlock.Bullet -> Text(
            text = buildAnnotatedString { append("•  "); append(inline(block.text)) },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        )
        is ReadmeBlock.Paragraph -> Text(
            text = inline(block.text),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

package com.vibeactions.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization

/** Keyboard for fields people write prose in (names, messages, prompts): the keyboard starts each
 *  sentence with a capital letter. Not for numbers, URLs, SSIDs, API keys or model ids. */
val SentenceKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

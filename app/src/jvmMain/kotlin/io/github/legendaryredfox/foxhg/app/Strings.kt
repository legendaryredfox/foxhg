package io.github.legendaryredfox.foxhg.app

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * A string of the app resources in the language now set, for code that is not composable: messages that view models and click handlers
 * write, labels of lists, titles of file dialogs. Inside a composable use `stringResource`. It waits while the resource file is read, which is quick.
 */
fun tr(resource: StringResource, vararg args: Any?): String = runBlocking { getString(resource, *args.map { it?.toString() ?: "?" }.toTypedArray()) }

/** The plural form of [resource] for [quantity], with [args] (the quantity itself is usually the first one). */
fun trPlural(resource: PluralStringResource, quantity: Int, vararg args: Any?): String =
    runBlocking { getPluralString(resource, quantity, *args.map { it?.toString() ?: "?" }.toTypedArray()) }

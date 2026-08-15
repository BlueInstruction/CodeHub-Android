package io.github.blueinstruction.codehub.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.blueinstruction.codehub.R
import io.github.blueinstruction.codehub.ui.components.CodeHubScaffold

@Composable
fun AiScreen() {
    CodeHubScaffold(title = stringResource(R.string.nav_ai)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "AI gateway has no provider configured.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

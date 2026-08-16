package codehub.ui.screens

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
import codehub.R
import codehub.ui.components.CodeHubScaffold

@Composable
fun TerminalScreen() {
    CodeHubScaffold(title = stringResource(R.string.nav_terminal)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Termux backend is not connected.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

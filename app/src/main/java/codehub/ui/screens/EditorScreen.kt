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
import io.codehub.R
import codehub.ui.components.CodeHubScaffold

@Composable
fun EditorScreen() {
    CodeHubScaffold(title = stringResource(R.string.nav_editor)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "code-server backend is not initialized.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

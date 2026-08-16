package codehub.core.workspace

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SafPathConverterTest {

    @Test
    fun `tree URI with primary volume resolves to external storage`() {
        val path = SafPathConverter.treeUriToPath(
            "content://com.android.externalstorage.documents/tree/primary%3ACodeHub"
        )
        assertThat(path).isEqualTo("/storage/emulated/0/CodeHub")
    }

    @Test
    fun `tree URI with non-primary volume resolves`() {
        val path = SafPathConverter.treeUriToPath(
            "content://com.android.externalstorage.documents/tree/1234-5678%3ACodeHub"
        )
        assertThat(path).isEqualTo("/storage/1234-5678/CodeHub")
    }

    @Test
    fun `document URI with primary resolves`() {
        val path = SafPathConverter.documentUriToPath(
            "content://com.android.externalstorage.documents/document/primary%3ACodeHub%2Fmyapp"
        )
        assertThat(path).isEqualTo("/storage/emulated/0/CodeHub/myapp")
    }

    @Test
    fun `uriToPath handles both tree and document URIs`() {
        val treePath = SafPathConverter.uriToPath(
            "content://com.android.externalstorage.documents/tree/primary%3ACodeHub"
        )
        assertThat(treePath).isEqualTo("/storage/emulated/0/CodeHub")

        val docPath = SafPathConverter.uriToPath(
            "content://com.android.externalstorage.documents/document/primary%3ACodeHub%2Fmyapp"
        )
        assertThat(docPath).isEqualTo("/storage/emulated/0/CodeHub/myapp")
    }

    @Test
    fun `non-content URI returns null`() {
        val path = SafPathConverter.uriToPath("file:///tmp/myapp")
        assertThat(path).isNull()
    }

    @Test
    fun `content URI without tree or document prefix returns null`() {
        val path = SafPathConverter.uriToPath(
            "content://com.example.provider/something/else"
        )
        assertThat(path).isNull()
    }

    @Test
    fun `URL-encoded spaces are decoded`() {
        val path = SafPathConverter.treeUriToPath(
            "content://com.android.externalstorage.documents/tree/primary%3AMy%20Documents"
        )
        assertThat(path).isEqualTo("/storage/emulated/0/My Documents")
    }

    @Test
    fun `nested paths are preserved`() {
        val path = SafPathConverter.documentUriToPath(
            "content://com.android.externalstorage.documents/document/primary%3ACodeHub%2Fworkspaces%2Fmyapp"
        )
        assertThat(path).isEqualTo("/storage/emulated/0/CodeHub/workspaces/myapp")
    }
}

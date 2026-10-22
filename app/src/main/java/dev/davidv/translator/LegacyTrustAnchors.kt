/*
 * Copyright (C) 2024 David V
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.davidv.translator

import android.content.Context
import android.os.Build
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

// Android ships ISRG Root X1 only from 7.1.1. The model hosts use Let's Encrypt chains that end
// there, so on older releases every download fails the handshake unless the root is added next
// to the system ones.
fun installIsrgRootOnLegacyAndroid(context: Context) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) return
  val systemRoots = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
  val roots = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
  systemRoots.aliases().toList().forEach { alias -> roots.setCertificateEntry(alias, systemRoots.getCertificate(alias)) }
  val isrgRoot =
    context.resources.openRawResource(R.raw.isrg_root_x1).use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
  roots.setCertificateEntry("isrg_root_x1", isrgRoot)
  val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(roots) }
  val tls = SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }
  HttpsURLConnection.setDefaultSSLSocketFactory(tls.socketFactory)
}

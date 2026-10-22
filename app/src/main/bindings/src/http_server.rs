use std::sync::{Arc, Mutex};

use thiserror::Error;
use translator::TranslatorSession;
use translator::http::{HttpServer, HttpServerConfig, ServeSettings, ServerHost};

use crate::uniffi_catalog::CatalogHandle;

/// Hands out the catalog and settings to serve a request with. The app
/// re-opens its catalog after downloads and migrations, and the user changes
/// settings while the server runs, so the server asks per request instead of
/// holding what it was started with.
#[uniffi::export(with_foreign)]
pub trait HttpServerHost: Send + Sync {
    fn catalog(&self) -> Option<Arc<CatalogHandle>>;
    fn settings(&self) -> ServeSettings;
}

struct ForeignServerHost(Arc<dyn HttpServerHost>);

impl ServerHost for ForeignServerHost {
    fn session(&self) -> Option<Arc<TranslatorSession>> {
        self.0.catalog().map(|catalog| catalog.session.clone())
    }

    fn settings(&self) -> ServeSettings {
        self.0.settings()
    }
}

#[derive(Debug, Error, uniffi::Error)]
pub enum HttpServerError {
    #[error("{reason}")]
    Start { reason: String },
}

#[derive(uniffi::Object)]
pub struct HttpServerHandle {
    server: Mutex<Option<HttpServer>>,
}

#[uniffi::export]
impl HttpServerHandle {
    /// Closes the socket and waits for in-flight requests to finish. A no-op
    /// after the first call.
    fn stop(&self) {
        let server = self.server.lock().expect("http server lock").take();
        if let Some(server) = server {
            server.stop();
        }
    }
}

#[uniffi::export]
pub fn start_http_server(
    config: HttpServerConfig,
    host: Arc<dyn HttpServerHost>,
) -> Result<Arc<HttpServerHandle>, HttpServerError> {
    crate::init_logging();
    let server = translator::http::start(
        config,
        Arc::new(ForeignServerHost(host)),
        Arc::new(crate::android_font_provider::AndroidFontProvider),
    )
    .map_err(|error| HttpServerError::Start {
        reason: error.to_string(),
    })?;
    Ok(Arc::new(HttpServerHandle {
        server: Mutex::new(Some(server)),
    }))
}

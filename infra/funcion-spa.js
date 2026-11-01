// CloudFront Function (viewer request) del frontend. Va aquí y no en las respuestas de error de la distribución
// porque esas aplican también a /api/*: un 404 de la API terminaría devolviendo index.html.
function handler(event) {
  var request = event.request;
  var host = request.headers.host ? request.headers.host.value : "";

  // www.<dominio> -> <dominio>
  if (host.indexOf("www.") === 0) {
    return {
      statusCode: 301,
      statusDescription: "Moved Permanently",
      headers: { location: { value: "https://" + host.substring(4) + request.uri } },
    };
  }

  // Rutas del SPA (/pacientes/123): todo lo que no es un archivo sirve index.html
  var ultimo = request.uri.split("/").pop();
  if (ultimo.indexOf(".") === -1) {
    request.uri = "/index.html";
  }
  return request;
}

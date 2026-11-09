// CloudFront Function (viewer request) del frontend. Va aquí y no en las respuestas de error de la distribución
// porque esas aplican también a /api/*: un 404 de la API terminaría devolviendo index.html.
//
// La raíz (/) es la landing pública (index.html). Las rutas de la app (/ingresar, /pacientes/123, /registro…) sirven
// app.html. Los archivos (con extensión) se sirven tal cual.
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

  if (request.uri === "/") {
    request.uri = "/index.html";
    return request;
  }

  var ultimo = request.uri.split("/").pop();
  if (ultimo.indexOf(".") === -1) {
    request.uri = "/app.html";
  }
  return request;
}

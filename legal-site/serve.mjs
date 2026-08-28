import { createReadStream, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { dirname, extname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const siteRoot = dirname(fileURLToPath(import.meta.url));
const port = Number.parseInt(process.env.MATA_LEGAL_SITE_PORT ?? '8765', 10);

const contentTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.txt', 'text/plain; charset=utf-8'],
]);

function resolveRequestPath(requestUrl) {
  const pathname = decodeURIComponent(new URL(requestUrl, 'http://127.0.0.1').pathname);
  let relativePath = pathname.replace(/^\/+/, '');
  if (relativePath === '' || pathname.endsWith('/')) {
    relativePath += 'index.html';
  } else if (extname(relativePath) === '') {
    relativePath += '/index.html';
  }

  const filePath = resolve(siteRoot, relativePath);
  if (filePath !== siteRoot && !filePath.startsWith(`${siteRoot}${sep}`)) {
    return null;
  }
  return filePath;
}

const server = createServer((request, response) => {
  const filePath = resolveRequestPath(request.url ?? '/');
  let responsePath = filePath;

  try {
    if (responsePath === null || !statSync(responsePath).isFile()) {
      response.statusCode = 404;
      responsePath = resolve(siteRoot, '404.html');
    }
  } catch {
    response.statusCode = 404;
    responsePath = resolve(siteRoot, '404.html');
  }

  response.setHeader('Content-Type', contentTypes.get(extname(responsePath)) ?? 'application/octet-stream');
  response.setHeader('Cache-Control', 'no-store');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  createReadStream(responsePath).pipe(response);
});

server.listen(port, '127.0.0.1', () => {
  console.log(`MATA legal site: http://127.0.0.1:${port}/`);
});

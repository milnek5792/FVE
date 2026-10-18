// jednoduchý cache-first service worker pro statické soubory
const CACHE_NAME = 'fve1-cache-v1';
const ASSETS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './service-worker.js',
  'https://unpkg.com/mqtt/dist/mqtt.min.js'
  // případně přidej ./icon-192.png, ./icon-512.png, pokud je máš
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(ASSETS))
  );
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(
        keys
          .filter(key => key !== CACHE_NAME)
          .map(key => caches.delete(key))
      )
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', event => {
  const { request } = event;

  // pro MQTT WSS nic necachujeme – to řeší přímo MQTT klient
  if (request.url.startsWith('wss://')) {
    return;
  }

  // cache-first pro naše statické soubory
  event.respondWith(
    caches.match(request).then(cached => {
      if (cached) return cached;
      return fetch(request);
    })
  );
});

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method === 'POST' && url.pathname.endsWith('index.html')) {
    event.respondWith((async () => {
      try {
        const formData = await event.request.formData();
        const files = formData.getAll('shared_images');
        const allClients = await self.clients.matchAll({ includeUncontrolled: true, type: 'window' });
        for (const client of allClients) {
          client.postMessage({ type: 'SHARED_GALLERY_FILES', files: files });
        }
      } catch (e) {}
      return Response.redirect('./index.html?share_received=1', 303);
    })());
  }
});

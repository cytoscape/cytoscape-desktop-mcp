/**
 * Cytoscape MCP stdio↔HTTP bridge — pure Node.js built-ins, no dependencies.
 *
 * Claude Desktop speaks MCP over stdio (newline-delimited JSON-RPC).
 * The Cytoscape MCP server speaks MCP over Streamable HTTP (POST /mcp).
 * This script bridges the two using only the built-in `http` module.
 *
 * Blocking: process.stdin.on('data') keeps the event loop alive for as long
 * as Claude Desktop holds the connection open.
 */

'use strict';

const http = require('http');

const PORT = parseInt(process.env.CYREST_PORT || '1234', 10);

let sessionId = null; // MCP session assigned by the server on first response
let stdinBuffer = '';

// Active stdin listener — this is the deterministic event-loop anchor.
process.stdin.on('data', (chunk) => {
  stdinBuffer += chunk.toString();
  let nl;
  while ((nl = stdinBuffer.indexOf('\n')) !== -1) {
    const line = stdinBuffer.slice(0, nl).trim();
    stdinBuffer = stdinBuffer.slice(nl + 1);
    if (line) forwardToServer(line);
  }
});

process.stdin.on('end', () => process.exit(0));

function forwardToServer(jsonLine) {
  const body = Buffer.from(jsonLine);

  const headers = {
    'Content-Type': 'application/json',
    'Content-Length': body.length,
    'Accept': 'application/json, text/event-stream',
  };
  if (sessionId) headers['Mcp-Session-Id'] = sessionId;

  const req = http.request(
    { hostname: 'localhost', port: PORT, path: '/mcp', method: 'POST', headers },
    (res) => {
      // Capture session ID for all subsequent requests.
      if (res.headers['mcp-session-id']) sessionId = res.headers['mcp-session-id'];

      const ct = res.headers['content-type'] || '';

      if (ct.includes('text/event-stream')) {
        // SSE: extract data lines and forward each JSON-RPC message to stdout.
        let sseBuffer = '';
        res.on('data', (chunk) => {
          sseBuffer += chunk.toString();
          const lines = sseBuffer.split('\n');
          sseBuffer = lines.pop(); // hold back any incomplete line
          for (const l of lines) {
            if (l.startsWith('data: ')) {
              const data = l.slice(6).trim();
              if (data && data !== '[DONE]') process.stdout.write(data + '\n');
            }
          }
        });
      } else {
        // Plain JSON response.
        let responseBody = '';
        res.on('data', (chunk) => (responseBody += chunk));
        res.on('end', () => {
          if (responseBody.trim()) process.stdout.write(responseBody.trim() + '\n');
        });
      }
    }
  );

  req.on('error', (err) => {
    // Return a JSON-RPC error so Claude Desktop knows the call failed.
    let id = null;
    try { id = JSON.parse(jsonLine).id ?? null; } catch (_) {}
    if (id !== null) {
      process.stdout.write(
        JSON.stringify({
          jsonrpc: '2.0',
          id,
          error: { code: -32603, message: `Cannot reach Cytoscape on port ${PORT}: ${err.message}` },
        }) + '\n'
      );
    }
  });

  req.write(body);
  req.end();
}

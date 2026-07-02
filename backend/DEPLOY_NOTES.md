# Deploy notes

- The full `.env` is written from the `BACKEND_ENV` repo secret on every deploy.
- Individual repo secrets (`OPENAI_API_KEY`, `YAHOO_CLIENT_ID`, `YAHOO_CLIENT_SECRET`,
  `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`, `MICROSOFT_TENANT`) override the corresponding
  line, last-wins. Rotating the OpenAI key = update the `OPENAI_API_KEY` repo secret and re-run the
  "Deploy backend to EC2" workflow.
- AI availability can be checked any time at `GET /health/ai` (sanitized; never returns the key).

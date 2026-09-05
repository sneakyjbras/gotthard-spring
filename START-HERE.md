# Start here

```bash
./start.sh
```

That is the whole thing. It brings up PostgreSQL, applies the migrations and
starts the backend on <http://localhost:8080>.

No API key is required. If `ANTHROPIC_API_KEY` happens to be set, AI analyses
run against Claude; if it is not, a deterministic stub is used instead and the
interface says so.

- `./start.sh --down` — stop, keep the data
- `./start.sh --reset` — stop, delete the data
- `./start.sh --help` — everything else

Further reading lives in [`docs/`](docs/), or run `mkdocs serve` for the
rendered site.

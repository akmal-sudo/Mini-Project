#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "============================================================"
echo "  🚀 Starting DarkStoreOS (Concurrency-Aware Dark Store)"
echo "============================================================"

# 1. Start PostgreSQL if not already running on port 5432
PG_BIN="/tmp/embedded-pg/PG-1a75b2fdb57c5cfc8674f29ebb0d831b/bin"
PGDATA="$DIR/.pgdata"

if nc -z localhost 5432 2>/dev/null; then
  echo "✅ PostgreSQL is already running on port 5432."
else
  echo "📦 Initializing and starting local PostgreSQL database..."
  mkdir -p "$PGDATA"
  if [ ! -f "$PGDATA/PG_VERSION" ]; then
    if [ -x "$PG_BIN/initdb" ]; then
      "$PG_BIN/initdb" -D "$PGDATA" -U postgres -A trust --no-locale >/dev/null 2>&1
    else
      echo "⚠️ Cannot find embedded postgres binary at $PG_BIN. Please ensure PostgreSQL is running on port 5432."
    fi
  fi

  if [ -x "$PG_BIN/pg_ctl" ]; then
    "$PG_BIN/pg_ctl" -D "$PGDATA" -l "$PGDATA/pg.log" start || true
    sleep 2
    echo "✅ Local PostgreSQL server started on port 5432."
  fi
fi

# Set database environment variables for backend
export DB_HOST="localhost"
export DB_PORT="5432"
export DB_NAME="${DB_NAME:-inventory_db}"
export DB_USER="postgres"
export DB_PASSWORD="postgres"

# Cleanup trap on Ctrl+C
cleanup() {
  echo ""
  echo "🛑 Shutting down DarkStoreOS..."
  if [ -n "$BACKEND_PID" ]; then
    kill "$BACKEND_PID" 2>/dev/null || true
  fi
  if [ -n "$FRONTEND_PID" ]; then
    kill "$FRONTEND_PID" 2>/dev/null || true
  fi
  echo "👋 Goodbye!"
  exit 0
}
trap cleanup SIGINT SIGTERM EXIT

# 2. Start Backend
echo "☕ Starting Spring Boot backend on http://localhost:8080..."
cd "$DIR/backend"
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslmode=disable --spring.datasource.username=${DB_USER} --spring.datasource.password=${DB_PASSWORD}" > "$DIR/backend.log" 2>&1 &
BACKEND_PID=$!
cd "$DIR"

echo "⏳ Waiting for backend to become healthy..."
RETRIES=60
until curl -s http://localhost:8080/actuator/health | grep '"status":"UP"' >/dev/null 2>&1 || [ $RETRIES -eq 0 ]; do
  sleep 1
  RETRIES=$((RETRIES-1))
done

if [ $RETRIES -eq 0 ]; then
  echo "❌ Backend failed to start within 60s. Check backend.log:"
  tail -n 25 "$DIR/backend.log"
  exit 1
fi
echo "✅ Backend is UP and healthy on http://localhost:8080!"

# 3. Start Frontend
echo "⚛️ Starting React + Vite frontend on http://localhost:5173..."
cd "$DIR/frontend"
npm run dev &
FRONTEND_PID=$!
cd "$DIR"

sleep 2

echo ""
echo "============================================================"
echo "  🎉 DarkStoreOS is LIVE and ready for demo!"
echo "============================================================"
echo "  🌐 Web Dashboard:    http://localhost:5173"
echo "  🔌 Backend REST API: http://localhost:8080"
echo "  📚 Swagger OpenAPI:  http://localhost:8080/swagger-ui.html"
echo "  📈 Prometheus:       http://localhost:8080/actuator/prometheus"
echo ""
echo "  🔑 Demo Accounts (Constant Passwords):"
echo "     • Admin:                     admin      / Admin@123"
echo "     • Warehouse Manager:         warehouse1 / Wh@12345"
echo "     • Vendor 1 (FreshFarms):     vendor1    / Vendor@123"
echo "     • Vendor 2 (Metro Wholesale): vendor2   / Vendor@123"
echo "     • Vendor 3 (Aura Care):      vendor3    / Vendor@123"
echo "============================================================"
echo "  Press Ctrl+C to stop all services."
echo ""

wait

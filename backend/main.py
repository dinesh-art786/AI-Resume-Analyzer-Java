
import os
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from database.db import initialize_database
from routes.auth_routes import router as auth_router
from routes.resume_routes import router as resume_router


# ==========================================
# BACKEND DIRECTORY
# ==========================================

BASE_DIR = Path(__file__).resolve().parent


# ==========================================
# INITIALIZE DATABASE
# ==========================================

initialize_database()


# ==========================================
# CREATE FASTAPI APPLICATION
# ==========================================

app = FastAPI(
    title="AI Resume Analyzer API",
    version="1.0.0"
)


# ==========================================
# CREATE UPLOADS DIRECTORY
# ==========================================

UPLOAD_DIR = BASE_DIR / "uploads"

UPLOAD_DIR.mkdir(
    parents=True,
    exist_ok=True
)


# ==========================================
# SERVE UPLOADED PROFILE PICTURES
# ==========================================

app.mount(
    "/uploads",
    StaticFiles(directory=str(UPLOAD_DIR)),
    name="uploads"
)


# ==========================================
# CONFIGURE CORS
# ==========================================

frontend_origins = [
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://localhost:5177",
    "http://127.0.0.1:5177",
    "https://airesumeanaliserjava.vercel.app",
]

# Read deployed frontend URL from Render environment variables
vercel_url = os.getenv("FRONTEND_URL", "").strip().rstrip("/")

if vercel_url and vercel_url not in frontend_origins:
    frontend_origins.append(vercel_url)

app.add_middleware(
    CORSMiddleware,
    allow_origins=frontend_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ==========================================
# REGISTER AUTHENTICATION ROUTER
# ==========================================

app.include_router(
    auth_router,
    prefix="/api"
)


# ==========================================
# REGISTER RESUME ROUTER
# ==========================================

app.include_router(
    resume_router,
    prefix="/api"
)


# ==========================================
# HOME ROUTE
# ==========================================

@app.get("/")
def home():
    return {
        "message": "AI Resume Analyzer backend is running"
    }


# ==========================================
# HEALTH CHECK
# ==========================================

@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "message": "Backend is working properly"
    }

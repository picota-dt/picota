import {createContext, ReactNode, useContext, useEffect, useState} from "react";

export type TwinStatus = "active" | "draft" | "offline";

export interface Variable {
    id: string;
    name: string;
    description?: string;
    unit: string;
    value: number;
    inferred?: boolean;
    variableType?: "sensor" | "inferred";
    dataType?: "numeric" | "categorical";
}

export interface DigitalSubject {
    id: string;
    name: string;
    variables: Variable[];
}

export interface InferredVariableResult {
    name: string;
    mae?: number;
    r2?: number;
    validationSampleCount?: number;
    validationDurationSeconds?: number;
    dataType?: "numeric" | "categorical";
    accuracy?: number;
    macroF1?: number;
    violations?: number;
    constraintViolations?: Record<string, number>;
}

export interface RetrainingConfig {
    enabled: boolean;
    schedule: "daily" | "weekly" | "monthly";
    minNewRecords: number;
    timeOfDay: string;
}

export interface InferenceEngine {
    trained: boolean;
    algorithm: string;
    trainedAt?: string | number;
    epochs?: number;
    learningRate?: number;
    windowSize?: number;
    batchSize?: number;
    inferredVariables?: InferredVariableResult[];
    retrainingConfig?: RetrainingConfig;
}

export type TrainingJobStatus = "queued" | "preparing" | "training" | "evaluating" | "done" | "failed";

export interface TrainingJob {
    jobId: string;
    twinId: string;
    status: TrainingJobStatus;
    progress?: number;
    currentPhase?: string;
    createdAt?: string;
    startedAt?: string;
    completedAt?: string;
    errorMessage?: string;
    result?: InferenceEngine;
}

export interface VariableStat {
    count: number;
    mean: number;
    std: number;
    min: number;
    max: number;
    median: number;
}

export interface SubjectDataset {
    subjectId: string;
    fileName?: string;
    uploadedRecords: number;
    realtimeRecords: number;
    uploadedAt?: string;
    stats: Record<string, VariableStat>;
}

export interface TelemetryPoint {
    time: string;
    value: number;
}

export interface VariableTelemetry {
    variableId: string;
    variableName: string;
    unit: string;
    current: number;
    history: TelemetryPoint[];
}

export interface DigitalTwin {
    id: string;
    name: string;
    description: string;
    version: string;
    image: string;
    type: string;
    status: TwinStatus;
    updatedAt: string;
    creditsUsed: number;
    model: string;
    subjects: DigitalSubject[];
    inferenceEngine: InferenceEngine | null;
    datasets: SubjectDataset[];
}

interface RawVariable {
    id?: string;
    name?: string;
    description?: string;
    unit?: string;
    value?: number;
    inferred?: boolean;
    variableType?: string;
    dataType?: string;
}

interface RawDigitalSubject {
    id?: string;
    name?: string;
    variables?: RawVariable[];
}

interface RawDigitalTwin extends Omit<DigitalTwin, "subjects" | "datasets"> {
    subjects?: RawDigitalSubject[];
    datasets?: SubjectDataset[];
}

export interface User {
    id?: string;
    name: string;
    email: string;
    credits: number;
    avatarInitials: string;
    joinedAt: string;
}

interface AuthResponse {
    token: string;
    expiresIn?: number;
    user: User;
}

interface AppContextValue {
    user: User;
    twins: DigitalTwin[];
    setTwins: React.Dispatch<React.SetStateAction<DigitalTwin[]>>;
    getTwin: (id: string) => DigitalTwin | undefined;
    updateProfile: (updates: { name?: string; email?: string }) => Promise<User>;
    changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
    deleteAccount: () => Promise<void>;
    updateTwin: (id: string, updates: Partial<DigitalTwin>) => Promise<void>;
    deleteTwin: (id: string) => Promise<void>;
    createTwin: (name: string, description: string, type: string, image: string) => Promise<DigitalTwin>;
    uploadDataset: (
        twinId: string,
        subjectId: string,
        file: File,
        onProgress?: (progressPercent: number | null) => void
    ) => Promise<SubjectDataset>;
    deleteDataset: (twinId: string, subjectId: string) => Promise<void>;
    getSubjectTelemetry: (twinId: string, subjectId: string, historyPoints?: number) => Promise<VariableTelemetry[]>;
    launchTrainingJob: (twinId: string) => Promise<TrainingJob>;
    getTrainingJob: (twinId: string, jobId: string) => Promise<TrainingJob>;
    isLoggedIn: boolean;
    loading: boolean;
    login: (email: string, password: string) => Promise<void>;
    register: (name: string, email: string, password: string) => Promise<void>;
    logout: () => Promise<void>;
}

const EMPTY_USER: User = {
    name: "",
    email: "",
    credits: 0,
    avatarInitials: "--",
    joinedAt: "-",
};

const TOKEN_KEY = "picota.auth.token";
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/v1";

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({children}: { children: ReactNode }) {
    const [user, setUser] = useState<User>(EMPTY_USER);
    const [twins, setTwins] = useState<DigitalTwin[]>([]);
    const [isLoggedIn, setIsLoggedIn] = useState<boolean>(!!localStorage.getItem(TOKEN_KEY));
    const [loading, setLoading] = useState<boolean>(true);
    const [token, setToken] = useState<string | null>(localStorage.getItem(TOKEN_KEY));

    useEffect(() => {
        let cancelled = false;
        const bootstrap = async () => {
            if (!token) {
                if (!cancelled) setLoading(false);
                return;
            }
            try {
                const [profile, catalog] = await Promise.all([
                    apiRequest<User>("/users/me", {token}),
                    apiRequest<DigitalTwin[]>("/twins", {token}),
                ]);
                if (!cancelled) {
                    setUser(profile);
                    setTwins(catalog);
                    setIsLoggedIn(true);
                }
            } catch {
                if (!cancelled) clearSession();
            } finally {
                if (!cancelled) setLoading(false);
            }
        };
        bootstrap();
        return () => {
            cancelled = true;
        };
    }, [token]);

    const clearSession = () => {
        localStorage.removeItem(TOKEN_KEY);
        setToken(null);
        setUser(EMPTY_USER);
        setTwins([]);
        setIsLoggedIn(false);
    };

    const setSession = (nextToken: string, nextUser: User) => {
        localStorage.setItem(TOKEN_KEY, nextToken);
        setToken(nextToken);
        setUser(nextUser);
        setIsLoggedIn(true);
    };

    const login = async (email: string, password: string) => {
        const auth = await apiRequest<AuthResponse>("/auth/login", {
            method: "POST",
            body: {email, password},
            skipAuth: true,
        });
        setSession(auth.token, auth.user);
        const catalog = await apiRequest<DigitalTwin[]>("/twins", {token: auth.token});
        setTwins(catalog);
    };

    const register = async (name: string, email: string, password: string) => {
        const auth = await apiRequest<AuthResponse>("/auth/register", {
            method: "POST",
            body: {name, email, password},
            skipAuth: true,
        });
        setSession(auth.token, auth.user);
        const catalog = await apiRequest<DigitalTwin[]>("/twins", {token: auth.token});
        setTwins(catalog);
    };

    const logout = async () => {
        try {
            if (token) await apiRequest<void>("/auth/logout", {method: "POST", token});
        } finally {
            clearSession();
        }
    };

    const getTwin = (id: string) => twins.find((t) => t.id === id);

    const updateProfile = async (updates: { name?: string; email?: string }): Promise<User> => {
        const activeToken = token;
        if (!activeToken) throw new Error("No authenticated session");
        const updated = await apiRequest<User>("/users/me", {
            method: "PATCH",
            body: updates,
            token: activeToken,
        });
        setUser(updated);
        return updated;
    };

    const changePassword = async (currentPassword: string, newPassword: string) => {
        const activeToken = token;
        if (!activeToken) throw new Error("No authenticated session");
        await apiRequest<void>("/auth/change-password", {
            method: "POST",
            body: {currentPassword, newPassword},
            token: activeToken,
        });
    };

    const deleteAccount = async () => {
        const activeToken = token;
        if (!activeToken) throw new Error("No authenticated session");
        await apiRequest<void>("/users/me", {
            method: "DELETE",
            token: activeToken,
        });
        clearSession();
    };

    const updateTwin = async (id: string, updates: Partial<DigitalTwin>) => {
        const activeToken = token;
        if (!activeToken) throw new Error("No authenticated session");
        const updated = await apiRequest<DigitalTwin>(`/twins/${id}`, {
            method: "PATCH",
            body: updates,
            token: activeToken,
        });
        setTwins((prev) => prev.map((t) => (t.id === id ? updated : t)));
    };

    const deleteTwin = async (id: string): Promise<void> => {
        const activeToken = token;
        if (!activeToken) throw new Error("No authenticated session");
        await apiRequest<void>(`/twins/${encodeURIComponent(id)}`, {
            method: "DELETE",
            token: activeToken,
        });
        setTwins((prev) => prev.filter((twin) => twin.id !== id));
    };

    const createTwin = async (name: string, description: string, type: string, image: string): Promise<DigitalTwin> => {
        const activeToken = token;
        if (!activeToken) throw new Error("No authenticated session");
        let created = await apiRequest<DigitalTwin>("/twins", {
            method: "POST",
            body: {name, description, type},
            token: activeToken,
        });
        if (image && created.image !== image) {
            created = await apiRequest<DigitalTwin>(`/twins/${created.id}`, {
                method: "PATCH",
                body: {image},
                token: activeToken,
            });
        }
        setTwins((prev) => [created, ...prev]);
        return created;
    };

    const uploadDataset = async (
        twinId: string,
        subjectId: string,
        file: File,
        onProgress?: (progressPercent: number | null) => void
    ): Promise<SubjectDataset> => {
        const activeToken = token;
        if (!activeToken) throw new Error("No authenticated session");
        const uploaded = await uploadMultipartWithProgress<SubjectDataset>({
            url: `${API_BASE_URL}/twins/${encodeURIComponent(twinId)}/datasets/${encodeURIComponent(subjectId)}`,
            token: activeToken,
            file,
            onProgress,
        });
        if (!uploaded) throw new Error("Dataset upload failed");
        setTwins((prev) =>
            prev.map((twin) => {
                if (twin.id !== twinId) return twin;
                const nextDatasets = [...(twin.datasets ?? []).filter((d) => d.subjectId !== subjectId), uploaded];
                return {...twin, datasets: nextDatasets};
            })
        );
        return uploaded;
    };

    const deleteDataset = async (twinId: string, subjectId: string): Promise<void> => {
        const activeToken = token;
        if (!activeToken) throw new Error("No authenticated session");
        await apiRequest<void>(
            `/twins/${encodeURIComponent(twinId)}/datasets/${encodeURIComponent(subjectId)}`,
            {method: "DELETE", token: activeToken}
        );
        setTwins((prev) =>
            prev.map((twin) => {
                if (twin.id !== twinId) return twin;
                return {...twin, datasets: (twin.datasets ?? []).filter((d) => d.subjectId !== subjectId)};
            })
        );
    };

    const getSubjectTelemetry = async (
        twinId: string,
        subjectId: string,
        historyPoints = 20
    ): Promise<VariableTelemetry[]> => {
        const activeToken = token;
        if (!activeToken) throw new Error("No authenticated session");
        const encodedPoints = Number.isFinite(historyPoints) ? Math.max(1, Math.floor(historyPoints)) : 20;
        return apiRequest<VariableTelemetry[]>(
            `/twins/${encodeURIComponent(twinId)}/subjects/${encodeURIComponent(subjectId)}/telemetry?historyPoints=${encodedPoints}`,
            {token: activeToken}
        );
    };

    const launchTrainingJob = async (twinId: string): Promise<TrainingJob> => {
        const activeToken = token;
        if (!activeToken) throw new Error("No authenticated session");
        const job = await apiRequest<TrainingJob>(
            `/twins/${encodeURIComponent(twinId)}/training-jobs`,
            {method: "POST", token: activeToken}
        );
        return normalizeTrainingJob(job);
    };

    const getTrainingJob = async (twinId: string, jobId: string): Promise<TrainingJob> => {
        const activeToken = token;
        if (!activeToken) throw new Error("No authenticated session");
        const job = await apiRequest<TrainingJob>(
            `/twins/${encodeURIComponent(twinId)}/training-jobs/${encodeURIComponent(jobId)}`,
            {token: activeToken}
        );
        const normalized = normalizeTrainingJob(job);
        const normalizedResult = normalized.status === "done" && normalized.result
            ? {
                ...normalized.result,
                trainedAt:
                    normalized.result.trainedAt ??
                    normalized.completedAt ??
                    normalized.startedAt ??
                    normalized.createdAt,
            }
            : normalized.result;
        const resolved = normalizedResult === normalized.result
            ? normalized
            : {...normalized, result: normalizedResult};
        if (resolved.status === "done" && resolved.result) {
            setTwins((prev) =>
                prev.map((candidate) => candidate.id === twinId
                    ? {...candidate, inferenceEngine: resolved.result!}
                    : candidate
                )
            );
        }
        return resolved;
    };

    return (
        <AppContext.Provider
            value={{
                user,
                twins,
                setTwins,
                getTwin,
                updateProfile,
                changePassword,
                deleteAccount,
                updateTwin,
                deleteTwin,
                createTwin,
                uploadDataset,
                deleteDataset,
                getSubjectTelemetry,
                launchTrainingJob,
                getTrainingJob,
                isLoggedIn,
                loading,
                login,
                register,
                logout,
            }}
        >
            {children}
        </AppContext.Provider>
    );
}

export function useApp() {
    const ctx = useContext(AppContext);
    if (!ctx) throw new Error("useApp must be used inside AppProvider");
    return ctx;
}

async function apiRequest<T>(
    path: string,
    options: {
        method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
        body?: unknown;
        token?: string | null;
        skipAuth?: boolean;
    } = {}
): Promise<T> {
    const {method = "GET", body, token, skipAuth = false} = options;
    const headers: Record<string, string> = {};
    if (!skipAuth && token) headers.Authorization = `Bearer ${token}`;
    const payload = body === undefined ? undefined : JSON.stringify(body);
    if (payload !== undefined) headers["Content-Type"] = "application/json";

    const response = await fetch(`${API_BASE_URL}${path}`, {
        method,
        headers,
        body: payload,
    });

    if (!response.ok) {
        const errorBody = await parseJsonSafe<{ message?: string }>(response);
        throw new Error(errorBody?.message ?? `HTTP ${response.status}`);
    }

    if (response.status === 204) return undefined as T;
    const parsed = await parseJsonSafe<unknown>(response);
    return normalizeApiPayload(path, parsed) as T;
}

async function parseJsonSafe<T>(response: Response): Promise<T | undefined> {
    const text = await response.text();
    if (!text) return undefined;
    return JSON.parse(text) as T;
}

async function uploadMultipartWithProgress<T>({
                                                  url,
                                                  token,
                                                  file,
                                                  onProgress,
                                              }: {
    url: string;
    token: string;
    file: File;
    onProgress?: (progressPercent: number | null) => void;
}): Promise<T> {
    return new Promise<T>((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.open("PUT", url, true);
        xhr.setRequestHeader("Authorization", `Bearer ${token}`);

        xhr.upload.onprogress = (event) => {
            if (!onProgress) return;
            if (event.lengthComputable && event.total > 0) {
                const progressPercent = Math.max(0, Math.min(100, Math.round((event.loaded / event.total) * 100)));
                onProgress(progressPercent);
            } else {
                onProgress(null);
            }
        };

        xhr.onerror = () => reject(new Error("Network error while uploading dataset"));
        xhr.onabort = () => reject(new Error("Dataset upload was canceled"));
        xhr.onload = () => {
            const responseText = xhr.responseText ?? "";
            if (xhr.status < 200 || xhr.status >= 300) {
                reject(new Error(extractErrorMessage(responseText) ?? `HTTP ${xhr.status}`));
                return;
            }
            if (!responseText || xhr.status === 204) {
                resolve(undefined as T);
                return;
            }
            try {
                resolve(JSON.parse(responseText) as T);
            } catch {
                reject(new Error("Invalid upload response from server"));
            }
        };

        const formData = new FormData();
        formData.append("file", file);
        onProgress?.(0);
        xhr.send(formData);
    });
}

function extractErrorMessage(responseText: string): string | undefined {
    if (!responseText) return undefined;
    try {
        const parsed = JSON.parse(responseText) as { message?: unknown };
        if (typeof parsed.message === "string" && parsed.message.trim().length > 0) {
            return parsed.message;
        }
    } catch {
    }
    return undefined;
}

function normalizeApiPayload(path: string, payload: unknown): unknown {
    if (payload == null) return payload;
    if (path === "/twins") {
        if (Array.isArray(payload)) return payload.map(normalizeTwin);
        if (isTwinLike(payload)) return normalizeTwin(payload);
        return payload;
    }
    if (path.startsWith("/twins/") && isTwinLike(payload)) return normalizeTwin(payload);
    return payload;
}

function isTwinLike(value: unknown): value is RawDigitalTwin {
    if (!value || typeof value !== "object") return false;
    const candidate = value as { id?: unknown; subjects?: unknown };
    return typeof candidate.id === "string" && Array.isArray(candidate.subjects);
}

function normalizeTwin(raw: RawDigitalTwin): DigitalTwin {
    return {
        ...raw,
        status: normalizeTwinStatus(raw.status),
        subjects: Array.isArray(raw.subjects) ? raw.subjects.map(normalizeSubject) : [],
        datasets: Array.isArray(raw.datasets) ? raw.datasets : [],
    };
}

function normalizeTwinStatus(status: unknown): TwinStatus {
    if (typeof status === "string") {
        const normalized = status.toLowerCase();
        if (normalized === "active" || normalized === "draft" || normalized === "offline") return normalized;
    }
    return "draft";
}

function normalizeSubject(raw: RawDigitalSubject): DigitalSubject {
    return {
        id: typeof raw.id === "string" && raw.id.length > 0 ? raw.id : `subject_${Math.random().toString(36).slice(2, 8)}`,
        name: typeof raw.name === "string" && raw.name.length > 0 ? raw.name : "Unnamed subject",
        variables: Array.isArray(raw.variables) ? raw.variables.map(normalizeVariable) : [],
    };
}

function normalizeVariable(raw: RawVariable): Variable {
    const variableType = normalizeVariableType(raw.variableType);
    const inferred = typeof raw.inferred === "boolean" ? raw.inferred : variableType === "inferred";
    return {
        id: typeof raw.id === "string" && raw.id.length > 0
            ? raw.id
            : (typeof raw.name === "string" && raw.name.length > 0 ? raw.name : `var_${Math.random().toString(36).slice(2, 8)}`),
        name: typeof raw.name === "string" && raw.name.length > 0 ? raw.name : "variable",
        description: typeof raw.description === "string" && raw.description.length > 0 ? raw.description : undefined,
        unit: typeof raw.unit === "string" ? raw.unit : "",
        value: normalizeNumericValue(raw.value, raw.id, raw.name),
        inferred,
        variableType,
        dataType: normalizeDataType(raw.dataType),
    };
}

function normalizeVariableType(value: unknown): "sensor" | "inferred" {
    if (typeof value !== "string") return "sensor";
    const normalized = value.toLowerCase();
    return normalized === "inferred" ? "inferred" : "sensor";
}

function normalizeDataType(value: unknown): "numeric" | "categorical" {
    if (typeof value !== "string") return "numeric";
    const normalized = value.toLowerCase();
    return normalized === "categorical" ? "categorical" : "numeric";
}

function normalizeNumericValue(value: unknown, id?: string, name?: string): number {
    if (typeof value === "number" && Number.isFinite(value)) return value;
    const seed = `${id ?? ""}|${name ?? ""}`;
    return syntheticValue(seed);
}

function syntheticValue(seed: string): number {
    let hash = 0;
    for (let i = 0; i < seed.length; i++) hash = (hash * 31 + seed.charCodeAt(i)) >>> 0;
    const value = 5 + (hash % 9000) / 100;
    return Number(value.toFixed(3));
}

function normalizeTrainingJob(job: TrainingJob): TrainingJob {
    return {
        ...job,
        status: normalizeTrainingJobStatus(job.status),
    };
}

function normalizeTrainingJobStatus(status: unknown): TrainingJobStatus {
    if (typeof status !== "string") return "queued";
    const normalized = status.toLowerCase();
    if (
        normalized === "queued" ||
        normalized === "preparing" ||
        normalized === "training" ||
        normalized === "evaluating" ||
        normalized === "done" ||
        normalized === "failed"
    ) {
        return normalized;
    }
    return "queued";
}

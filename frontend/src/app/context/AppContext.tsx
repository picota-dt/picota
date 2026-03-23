import {createContext, useContext, useEffect, useState, ReactNode} from "react";

export type TwinStatus = "active" | "draft" | "offline";

export interface Variable {
    id: string;
    name: string;
    unit: string;
    value: number;
    inferred?: boolean;
}

export interface DigitalSubject {
    id: string;
    name: string;
    variables: Variable[];
}

export interface InferredVariableResult {
    name: string;
    accuracy: number;
    mae: number;
    violations: number;
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
    trainedAt?: string;
    epochs?: number;
    learningRate?: number;
    windowSize?: number;
    batchSize?: number;
    inferredVariables?: InferredVariableResult[];
    retrainingConfig?: RetrainingConfig;
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

export interface User {
    id?: string;
    name: string;
    email: string;
    role: string;
    credits: number;
    avatarInitials: string;
    joinedAt: string;
    organization: string;
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
    updateTwin: (id: string, updates: Partial<DigitalTwin>) => Promise<void>;
    createTwin: (name: string, description: string, type: string, image: string) => Promise<DigitalTwin>;
    isLoggedIn: boolean;
    loading: boolean;
    login: (email: string, password: string) => Promise<void>;
    register: (name: string, email: string, password: string, organization?: string) => Promise<void>;
    logout: () => Promise<void>;
}

const GUEST_USER: User = {
    name: "Guest",
    email: "guest@picota.local",
    role: "Engineer",
    credits: 0,
    avatarInitials: "GU",
    joinedAt: "-",
    organization: "Picota",
};

const TOKEN_KEY = "picota.auth.token";
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/v1";

const AppContext = createContext<AppContextValue | null>(null);

export function AppProvider({children}: { children: ReactNode }) {
    const [user, setUser] = useState<User>(GUEST_USER);
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
        setUser(GUEST_USER);
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

    const register = async (name: string, email: string, password: string, organization?: string) => {
        const auth = await apiRequest<AuthResponse>("/auth/register", {
            method: "POST",
            body: {name, email, password, organization},
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

    return (
        <AppContext.Provider
            value={{
                user,
                twins,
                setTwins,
                getTwin,
                updateTwin,
                createTwin,
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
    return (await parseJsonSafe<T>(response)) as T;
}

async function parseJsonSafe<T>(response: Response): Promise<T | undefined> {
    const text = await response.text();
    if (!text) return undefined;
    return JSON.parse(text) as T;
}

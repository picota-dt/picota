import {useEffect, useMemo, useRef, useState} from "react";
import {Link, useLocation, useNavigate} from "react-router";
import {LoaderCircle} from "lucide-react";
import {useApp} from "../context/AppContext";

declare global {
    interface Window {
        google?: {
            accounts?: {
                id?: {
                    initialize: (options: Record<string, unknown>) => void;
                    renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void;
                };
            };
        };
    }
}

export default function LoginPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const {getGoogleClientId, signInWithGoogle, isLoggedIn} = useApp();
    const redirectPath = useMemo(() => resolveRedirectPath(location.search), [location.search]);
    const buttonRef = useRef<HTMLDivElement | null>(null);
    const [clientId, setClientId] = useState("");
    const [loadingConfig, setLoadingConfig] = useState(true);
    const [renderingButton, setRenderingButton] = useState(false);
    const [signingIn, setSigningIn] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!isLoggedIn) return;
        navigate(redirectPath, {replace: true});
    }, [isLoggedIn, navigate, redirectPath]);

    useEffect(() => {
        let cancelled = false;
        const loadConfig = async () => {
            setLoadingConfig(true);
            setError("");
            try {
                const resolvedClientId = await getGoogleClientId();
                if (cancelled) return;
                setClientId(resolvedClientId);
            } catch (err) {
                if (cancelled) return;
                setError(err instanceof Error ? err.message : "Unable to initialize Google sign-in.");
            } finally {
                if (!cancelled) setLoadingConfig(false);
            }
        };
        loadConfig();
        return () => {
            cancelled = true;
        };
    }, [getGoogleClientId]);

    useEffect(() => {
        if (!clientId || !buttonRef.current) return;
        let cancelled = false;
        setRenderingButton(true);
        setError("");

        loadGoogleIdentityScript()
            .then(() => {
                if (cancelled || !buttonRef.current || !window.google?.accounts?.id) return;
                const parent = buttonRef.current;
                parent.innerHTML = "";
                window.google.accounts.id.initialize({
                    client_id: clientId,
                    callback: async (response: Record<string, unknown>) => {
                        const credential = typeof response?.credential === "string" ? response.credential : "";
                        if (!credential) {
                            setError("Google did not return a valid credential.");
                            return;
                        }
                        setSigningIn(true);
                        setError("");
                        try {
                            await signInWithGoogle(credential);
                            navigate(redirectPath, {replace: true});
                        } catch (err) {
                            setError(err instanceof Error ? err.message : "Unable to sign in with Google.");
                        } finally {
                            setSigningIn(false);
                        }
                    },
                });
                window.google.accounts.id.renderButton(parent, {
                    type: "standard",
                    theme: "outline",
                    text: "continue_with",
                    shape: "pill",
                    size: "large",
                    width: 320,
                    logo_alignment: "left",
                });
            })
            .catch(() => {
                if (!cancelled) setError("Unable to load Google sign-in.");
            })
            .finally(() => {
                if (!cancelled) setRenderingButton(false);
            });

        return () => {
            cancelled = true;
        };
    }, [clientId, navigate, redirectPath, signInWithGoogle]);

    const busy = loadingConfig || renderingButton || signingIn;

    return (
        <div className="min-h-screen bg-[#0f1117] flex items-center justify-center px-4">
            <div className="w-full max-w-sm">
                <div className="flex items-center justify-center gap-2.5 mb-8">
                    <div
                        className="w-8 h-8 rounded-xl bg-cyan-500 flex items-center justify-center shadow-lg shadow-cyan-500/30">
                        <img src="/icons/icon-512.png" alt="Picota" className="w-4 h-4 object-contain"/>
                    </div>
                    <span className="text-white" style={{fontWeight: 700, fontSize: "1.1rem"}}>Picota</span>
                </div>

                <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-7">
                    <h1 className="text-white mb-1" style={{fontWeight: 700, fontSize: "1.2rem"}}>Continue with
                        Google</h1>
                    <p className="text-white/40 text-sm mb-6">Use your Google account to access Picota.</p>

                    <div className="rounded-2xl border border-white/8 bg-white/[0.03] p-5">
                        <div className="flex items-center gap-3 mb-4">
                            <div className="w-10 h-10 rounded-2xl bg-white/6 flex items-center justify-center">
                                <img src="/icons/icon-512.png" alt="Picota" className="w-5 h-5 object-contain"/>
                            </div>
                            <div>
                                <p className="text-white text-sm" style={{fontWeight: 600}}>Single sign-on</p>
                                <p className="text-white/35 text-xs">Google is the only authentication provider
                                    enabled.</p>
                            </div>
                        </div>

                        <div className="min-h-12 flex items-center justify-center">
                            {busy && (
                                <div className="flex items-center gap-2 text-white/45 text-sm">
                                    <LoaderCircle className="w-4 h-4 animate-spin"/>
                                    {signingIn ? "Signing in…" : "Loading Google sign-in…"}
                                </div>
                            )}
                            <div ref={buttonRef} className={busy ? "hidden" : ""}/>
                        </div>
                    </div>

                    {error && <p className="text-red-400 text-sm mt-4">{error}</p>}
                </div>

                <p className="text-center mt-5">
                    <Link to="/" className="text-white/25 text-xs hover:text-white/40 transition-colors">
                        ← Back to home
                    </Link>
                </p>
            </div>
        </div>
    );
}

function resolveRedirectPath(search: string): string {
    const raw = new URLSearchParams(search).get("redirect");
    if (!raw || !raw.startsWith("/") || raw.startsWith("//")) return "/twins";
    const normalized = raw.trim();
    if (normalized === "/login" || normalized.startsWith("/login?")) return "/twins";
    return normalized;
}

let googleIdentityScriptPromise: Promise<void> | null = null;

function loadGoogleIdentityScript(): Promise<void> {
    if (window.google?.accounts?.id) return Promise.resolve();
    if (googleIdentityScriptPromise) return googleIdentityScriptPromise;
    googleIdentityScriptPromise = new Promise<void>((resolve, reject) => {
        const existing = document.querySelector<HTMLScriptElement>('script[data-google-identity="true"]');
        if (existing) {
            existing.addEventListener("load", () => resolve(), {once: true});
            existing.addEventListener("error", () => reject(new Error("Unable to load Google Identity Services")), {once: true});
            return;
        }
        const script = document.createElement("script");
        script.src = "https://accounts.google.com/gsi/client";
        script.async = true;
        script.defer = true;
        script.dataset.googleIdentity = "true";
        script.onload = () => resolve();
        script.onerror = () => reject(new Error("Unable to load Google Identity Services"));
        document.head.appendChild(script);
    });
    return googleIdentityScriptPromise;
}

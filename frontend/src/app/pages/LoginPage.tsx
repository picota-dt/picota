import {useState} from "react";
import {useNavigate, Link} from "react-router";
import {Eye, EyeOff} from "lucide-react";
import {useApp} from "../context/AppContext";

export default function LoginPage() {
    const navigate = useNavigate();
    const {login} = useApp();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [showPass, setShowPass] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!email || !password) {
            setError("Please fill in all fields.");
            return;
        }
        setError("");
        setLoading(true);
        try {
            await login(email, password);
            navigate("/twins");
        } catch (err) {
            setError(err instanceof Error ? err.message : "Unable to sign in.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-[#0f1117] flex items-center justify-center px-4">
            <div className="w-full max-w-sm">
                {/* Logo */}
                <div className="flex items-center justify-center gap-2.5 mb-8">
                    <div
                        className="w-8 h-8 rounded-xl bg-cyan-500 flex items-center justify-center shadow-lg shadow-cyan-500/30">
                        <img src="/icons/icon-512.png" alt="Picota" className="w-4 h-4 object-contain"/>
                    </div>
                    <span className="text-white" style={{fontWeight: 700, fontSize: "1.1rem"}}>Picota</span>
                </div>

                <div className="bg-[#1a1d27] border border-white/8 rounded-2xl p-7">
                    <h1 className="text-white mb-1" style={{fontWeight: 700, fontSize: "1.2rem"}}>Welcome back</h1>
                    <p className="text-white/40 text-sm mb-6">Sign in to your account</p>

                    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                        <div>
                            <label className="text-white/50 text-xs uppercase tracking-wider mb-1.5 block">Email</label>
                            <input
                                type="email"
                                placeholder="you@example.com"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 text-white placeholder-white/25 text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                                autoFocus
                            />
                        </div>
                        <div>
                            <label
                                className="text-white/50 text-xs uppercase tracking-wider mb-1.5 block">Password</label>
                            <div className="relative">
                                <input
                                    type={showPass ? "text" : "password"}
                                    placeholder="••••••••"
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    className="w-full bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 pr-10 text-white placeholder-white/25 text-sm outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all"
                                />
                                <button
                                    type="button"
                                    onClick={() => setShowPass(!showPass)}
                                    className="absolute right-3 top-1/2 -translate-y-1/2 text-white/30 hover:text-white/60 transition-colors"
                                >
                                    {showPass ? <EyeOff className="w-4 h-4"/> : <Eye className="w-4 h-4"/>}
                                </button>
                            </div>
                        </div>

                        {error && <p className="text-red-400 text-sm">{error}</p>}

                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full bg-cyan-500 hover:bg-cyan-400 disabled:opacity-50 text-white py-2.5 rounded-xl text-sm transition-all shadow-lg shadow-cyan-500/20 mt-1"
                            style={{fontWeight: 500}}
                        >
                            {loading ? "Signing in…" : "Sign in"}
                        </button>
                    </form>

                    <div className="mt-5 pt-5 border-t border-white/6 text-center">
                        <p className="text-white/35 text-sm">
                            Don't have an account?{" "}
                            <Link to="/register" className="text-cyan-400 hover:text-cyan-300 transition-colors">
                                Sign up
                            </Link>
                        </p>
                    </div>
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

import {ChevronDown, ChevronLeft, ExternalLink, LogOut, User} from "lucide-react";
import {useState, useRef, useEffect} from "react";
import {useNavigate, useMatch, Link} from "react-router";
import {useApp} from "../context/AppContext";

export function Navbar() {
    const navigate = useNavigate();
    const {user, logout, getTwin} = useApp();
    const [userMenuOpen, setUserMenuOpen] = useState(false);
    const menuRef = useRef<HTMLDivElement>(null);

    // Detect if we're on a twin detail page
    const twinMatch = useMatch("/twins/:id");
    const twin = twinMatch ? getTwin(twinMatch.params.id ?? "") : null;

    useEffect(() => {
        function handleClick(e: MouseEvent) {
            if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
                setUserMenuOpen(false);
            }
        }

        document.addEventListener("mousedown", handleClick);
        return () => document.removeEventListener("mousedown", handleClick);
    }, []);

    const handleLogout = async () => {
        await logout();
        navigate("/");
    };

    return (
        <header className="sticky top-0 z-50 bg-[#0f1117] border-b border-white/8">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="flex items-center h-14 gap-4">

                    {/* Logo */}
                    <Link to="/twins" className="flex items-center gap-2.5 flex-shrink-0 group">
                        <div
                            className="w-7 h-7 rounded-lg bg-cyan-500 flex items-center justify-center shadow-lg shadow-cyan-500/30">
                            <img src="/icons/icon-512.png" alt="Picota" className="w-4 h-4 object-contain"/>
                        </div>
                        <span className="text-white group-hover:text-cyan-300 transition-colors"
                              style={{fontWeight: 700, fontSize: "1rem"}}>
              Picota
            </span>
                    </Link>

                    {/* Breadcrumb / nav */}
                    <div className="flex items-center gap-1 flex-1 min-w-0">
                        {twin ? (
                            // Twin detail breadcrumb
                            <div className="flex items-center gap-1 text-sm min-w-0">
                                <button
                                    onClick={() => navigate("/twins")}
                                    className="flex items-center gap-1 text-white/40 hover:text-white/70 transition-colors flex-shrink-0"
                                >
                                    <ChevronLeft className="w-3.5 h-3.5"/>
                                    My Twins
                                </button>
                                <span className="text-white/20 flex-shrink-0">/</span>
                                <span className="text-white/70 truncate" style={{fontWeight: 500}}>
                  {twin.name}
                </span>
                            </div>
                        ) : (
                            // Default nav links
                            <nav className="flex items-center gap-5 ml-2">
                                <Link
                                    to="/twins"
                                    className="text-sm text-white/50 hover:text-white/80 transition-colors data-[active]:text-cyan-400"
                                    style={{fontWeight: 500}}
                                >
                                    My Twins
                                </Link>
                                <a
                                    href="#"
                                    className="text-sm text-white/50 hover:text-white/80 transition-colors flex items-center gap-1"
                                >
                                    Docs
                                    <ExternalLink className="w-3 h-3 opacity-50"/>
                                </a>
                            </nav>
                        )}
                    </div>

                    {/* User menu */}
                    <div className="relative flex-shrink-0" ref={menuRef}>
                        <button
                            onClick={() => setUserMenuOpen(!userMenuOpen)}
                            className="flex items-center gap-2 pl-1 pr-2 py-1 rounded-full hover:bg-white/8 transition-colors"
                        >
                            <div
                                className="w-7 h-7 rounded-full bg-gradient-to-br from-cyan-400 to-blue-500 flex items-center justify-center text-white text-xs flex-shrink-0"
                                style={{fontWeight: 600}}>
                                {user.avatarInitials}
                            </div>
                            <span className="text-white/60 text-sm hidden sm:block">{user.name.split(" ")[0]}</span>
                            <ChevronDown className="w-3.5 h-3.5 text-white/30 hidden sm:block"/>
                        </button>

                        {userMenuOpen && (
                            <div
                                className="absolute right-0 top-full mt-2 bg-[#1a1d27] border border-white/10 rounded-xl shadow-xl z-50 min-w-[180px] overflow-hidden">
                                {/* User info */}
                                <div className="px-4 py-3 border-b border-white/8">
                                    <p className="text-white text-sm" style={{fontWeight: 500}}>{user.name}</p>
                                    <p className="text-white/35 text-xs mt-0.5">{user.email}</p>
                                </div>
                                <div className="py-1">
                                    <button
                                        onClick={() => {
                                            navigate("/profile");
                                            setUserMenuOpen(false);
                                        }}
                                        className="w-full text-left px-4 py-2.5 text-sm text-white/60 hover:text-white hover:bg-white/5 transition-colors flex items-center gap-2"
                                    >
                                        <User className="w-3.5 h-3.5"/>
                                        Profile
                                    </button>
                                    <button
                                        onClick={handleLogout}
                                        className="w-full text-left px-4 py-2.5 text-sm text-red-400/70 hover:text-red-400 hover:bg-red-500/5 transition-colors flex items-center gap-2"
                                    >
                                        <LogOut className="w-3.5 h-3.5"/>
                                        Sign out
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </header>
    );
}

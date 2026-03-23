import {Activity, Clock, Tag, MoreHorizontal, Pencil, Trash2, Copy} from "lucide-react";
import {useState, useRef, useEffect} from "react";
import {useNavigate} from "react-router";
import {DigitalTwin, TwinStatus} from "../context/AppContext";

export type {TwinStatus};
export type {DigitalTwin};

const STATUS_CONFIG: Record<TwinStatus, { label: string; dot: string; text: string }> = {
    active: {label: "Active", dot: "bg-emerald-400", text: "text-emerald-400"},
    draft: {label: "Draft", dot: "bg-amber-400", text: "text-amber-400"},
    offline: {label: "Offline", dot: "bg-white/20", text: "text-white/40"},
};

export function TwinCard({twin}: { twin: DigitalTwin }) {
    const navigate = useNavigate();
    const [menuOpen, setMenuOpen] = useState(false);
    const menuRef = useRef<HTMLDivElement>(null);
    const status = STATUS_CONFIG[twin.status];

    useEffect(() => {
        function handleClick(e: MouseEvent) {
            if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
                setMenuOpen(false);
            }
        }

        document.addEventListener("mousedown", handleClick);
        return () => document.removeEventListener("mousedown", handleClick);
    }, []);

    return (
        <div
            className="group bg-[#1a1d27] border border-white/8 rounded-2xl overflow-hidden hover:border-cyan-500/40 hover:shadow-lg hover:shadow-cyan-500/5 transition-all duration-300 flex flex-col cursor-pointer"
            onClick={() => navigate(`/twins/${twin.id}`)}
        >
            {/* Thumbnail */}
            <div className="relative aspect-[16/9] overflow-hidden bg-[#0f1117]">
                <img
                    src={twin.image}
                    alt={twin.name}
                    className="w-full h-full object-cover opacity-80 group-hover:opacity-100 group-hover:scale-105 transition-all duration-500"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-[#1a1d27] via-transparent to-transparent"/>

                <div
                    className="absolute top-3 left-3 flex items-center gap-1.5 bg-black/50 backdrop-blur-sm rounded-full px-2.5 py-1">
                    <span
                        className={`w-1.5 h-1.5 rounded-full ${status.dot} ${twin.status === "active" ? "animate-pulse" : ""}`}/>
                    <span className={`text-xs ${status.text}`} style={{fontWeight: 500}}>{status.label}</span>
                </div>

                <div
                    className="absolute top-3 right-3 bg-black/50 backdrop-blur-sm rounded-full px-2.5 py-1 flex items-center gap-1">
                    <Tag className="w-3 h-3 text-cyan-400"/>
                    <span className="text-xs text-white/70">{twin.type}</span>
                </div>
            </div>

            {/* Body */}
            <div className="p-4 flex flex-col flex-1">
                <div className="flex items-start justify-between gap-2">
                    <h3
                        className="text-white line-clamp-1 group-hover:text-cyan-300 transition-colors"
                        style={{fontWeight: 600, fontSize: "0.95rem"}}
                    >
                        {twin.name}
                    </h3>

                    <div className="relative flex-shrink-0" ref={menuRef} onClick={(e) => e.stopPropagation()}>
                        <button
                            onClick={() => setMenuOpen(!menuOpen)}
                            className="w-7 h-7 flex items-center justify-center rounded-lg hover:bg-white/8 text-white/40 hover:text-white/70 transition-colors"
                        >
                            <MoreHorizontal className="w-4 h-4"/>
                        </button>
                        {menuOpen && (
                            <div
                                className="absolute right-0 top-full mt-1 bg-[#252836] border border-white/10 rounded-xl shadow-xl z-20 min-w-[150px] py-1">
                                {[
                                    {icon: Pencil, label: "Edit"},
                                    {icon: Copy, label: "Duplicate"},
                                    {icon: Trash2, label: "Delete", danger: true},
                                ].map(({icon: Icon, label, danger}) => (
                                    <button
                                        key={label}
                                        className={`w-full text-left px-3 py-2 text-sm flex items-center gap-2 transition-colors ${
                                            danger ? "text-red-400 hover:bg-red-500/10" : "text-white/60 hover:text-white hover:bg-white/5"
                                        }`}
                                    >
                                        <Icon className="w-3.5 h-3.5"/>
                                        {label}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                </div>

                <p className="text-white/40 text-sm mt-1.5 line-clamp-2 flex-1">{twin.description}</p>

                <div className="flex items-center justify-between mt-4 pt-3 border-t border-white/6">
                    <div className="flex items-center gap-1.5 text-white/30 text-xs">
                        <Clock className="w-3.5 h-3.5"/>
                        <span>{twin.updatedAt}</span>
                    </div>
                    <button
                        onClick={(e) => {
                            e.stopPropagation();
                            navigate(`/twins/${twin.id}`);
                        }}
                        className="flex items-center gap-1.5 text-cyan-500 hover:text-cyan-300 text-xs transition-colors"
                    >
                        <Activity className="w-3.5 h-3.5"/>
                        Open twin
                    </button>
                </div>
            </div>
        </div>
    );
}

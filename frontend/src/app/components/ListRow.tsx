import {Activity, Clock, Tag, MoreHorizontal} from "lucide-react";
import {useNavigate} from "react-router";
import {DigitalTwin} from "../context/AppContext";

const STATUS_CONFIG = {
    active: {label: "Active", dot: "bg-emerald-400", text: "text-emerald-400"},
    draft: {label: "Draft", dot: "bg-amber-400", text: "text-amber-400"},
    offline: {label: "Offline", dot: "bg-white/20", text: "text-white/30"},
};

export function ListRow({twin}: { twin: DigitalTwin }) {
    const navigate = useNavigate();
    const status = STATUS_CONFIG[twin.status];

    return (
        <div
            className="bg-[#1a1d27] border border-white/8 rounded-xl p-4 flex items-center gap-4 hover:border-cyan-500/30 transition-all duration-200 group cursor-pointer"
            onClick={() => navigate(`/twins/${twin.id}`)}
        >
            <div className="w-16 h-12 rounded-lg overflow-hidden flex-shrink-0 bg-[#0f1117]">
                <img
                    src={twin.image}
                    alt={twin.name}
                    className="w-full h-full object-cover opacity-70 group-hover:opacity-100 transition-opacity"
                />
            </div>

            <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                    <h3 className="text-white text-sm line-clamp-1 group-hover:text-cyan-300 transition-colors"
                        style={{fontWeight: 600}}>
                        {twin.name}
                    </h3>
                    <span className="hidden sm:flex items-center gap-1 bg-white/6 rounded-full px-2 py-0.5">
            <Tag className="w-3 h-3 text-cyan-400/60"/>
            <span className="text-white/40 text-xs">{twin.type}</span>
          </span>
                </div>
                <p className="text-white/35 text-xs mt-0.5 line-clamp-1">{twin.description}</p>
            </div>

            <div className="hidden sm:flex items-center gap-1.5 flex-shrink-0">
                <span
                    className={`w-1.5 h-1.5 rounded-full ${status.dot} ${twin.status === "active" ? "animate-pulse" : ""}`}/>
                <span className={`text-xs ${status.text}`}>{status.label}</span>
            </div>

            <div className="hidden md:flex items-center gap-1.5 text-white/25 text-xs flex-shrink-0 w-28">
                <Clock className="w-3.5 h-3.5"/>
                {twin.updatedAt}
            </div>

            <div className="flex items-center gap-2 flex-shrink-0" onClick={(e) => e.stopPropagation()}>
                <button
                    onClick={() => navigate(`/twins/${twin.id}`)}
                    className="flex items-center gap-1.5 text-cyan-500 hover:text-cyan-300 text-xs transition-colors"
                >
                    <Activity className="w-3.5 h-3.5"/>
                    <span className="hidden sm:inline">Open</span>
                </button>
                <button
                    className="w-7 h-7 flex items-center justify-center rounded-lg hover:bg-white/8 text-white/30 hover:text-white/60 transition-colors">
                    <MoreHorizontal className="w-4 h-4"/>
                </button>
            </div>
        </div>
    );
}

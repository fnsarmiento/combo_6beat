package engine.draw;

import engine.objects.GameObject;
import engine.objects.IDraw;
import engine.objects.IUpdate;
import java.util.ArrayList;
import processing.core.PApplet;
import processing.core.PGraphics;

/**
 * Sistema nativo de partículas cuadradas simples para efectos visuales en escenas del motor.
 */
public class ParticleSystem extends GameObject implements IUpdate, IDraw, Drawable {

	// =========================================================================
	// Estructura Interna de Partícula y Estado del Emisor
	// =========================================================================

	private static class Particle {
		float x, y;
		float vx, vy;
		float size;
		float life;
		float maxLife;
		float gravity;
		int color;
	}

	private final ArrayList<Particle> _particles = new ArrayList<>();
	private final int _maxParticles;
	private float _zIndex = 0.0f;
	private boolean _visible = true;

	// =========================================================================
	// Constructores y Emisión
	// =========================================================================

	public ParticleSystem() {
		this(256);
	}

	public ParticleSystem(int maxParticles) {
		this._maxParticles = Math.max(16, maxParticles);
	}

	/**
	 * Emite una partícula individual cuadrada con velocidad, tamaño, tiempo de vida y gravedad dados.
	 */
	public void emit(
		float x,
		float y,
		float vx,
		float vy,
		float size,
		float life,
		float gravity,
		int color
	) {
		if (_particles.size() >= _maxParticles) {
			_particles.remove(0);
		}
		Particle p = new Particle();
		p.x = x;
		p.y = y;
		p.vx = vx;
		p.vy = vy;
		p.size = Math.max(1.0f, size);
		p.life = Math.max(0.02f, life);
		p.maxLife = p.life;
		p.gravity = gravity;
		p.color = color;
		_particles.add(p);
	}

	/**
	 * Emite una ráfaga radial de partículas cuadradas (mini explosión).
	 */
	public void emitBurst(
		float x,
		float y,
		int count,
		float minSpeed,
		float maxSpeed,
		float minSize,
		float maxSize,
		float minLife,
		float maxLife,
		float gravity,
		int color
	) {
		for (int i = 0; i < count; i++) {
			double angle = Math.random() * Math.PI * 2.0;
			float speed = minSpeed + (float) Math.random() * (maxSpeed - minSpeed);
			float size = minSize + (float) Math.random() * (maxSize - minSize);
			float life = minLife + (float) Math.random() * (maxLife - minLife);
			emit(
				x,
				y,
				(float) Math.cos(angle) * speed,
				(float) Math.sin(angle) * speed - speed * 0.25f,
				size,
				life,
				gravity,
				color
			);
		}
	}

	public void clear() {
		_particles.clear();
	}

	// =========================================================================
	// Simulación Física y Renderizado
	// =========================================================================

	@Override
	public void update(float dt) {
		if (dt <= 0.0f) return;
		for (int i = _particles.size() - 1; i >= 0; i--) {
			Particle p = _particles.get(i);
			p.life -= dt;
			if (p.life <= 0.0f) {
				_particles.remove(i);
				continue;
			}
			p.vy += p.gravity * dt;
			p.x += p.vx * dt;
			p.y += p.vy * dt;
		}
	}

	@Override
	public void updateDraw(float dt) {
		// La simulación se actualiza en update(dt) para evitar doble avance en Scene
	}

	@Override
	public void draw(PGraphics g) {
		if (!_visible || _particles.isEmpty()) return;
		g.pushStyle();
		g.noStroke();
		for (int i = 0; i < _particles.size(); i++) {
			Particle p = _particles.get(i);
			float ratio = PApplet.constrain((p.life / p.maxLife) * 2.2f, 0.0f, 1.0f);
			int baseAlpha = (p.color >>> 24) & 0xFF;
			if (baseAlpha == 0) baseAlpha = 255;
			int alpha = Math.round(baseAlpha * ratio);
			int rgb = p.color & 0x00FFFFFF;
			g.fill((alpha << 24) | rgb);
			float half = p.size * 0.5f;
			g.rect(p.x - half, p.y - half, p.size, p.size);
		}
		g.popStyle();
	}

	// =========================================================================
	// Implementación de Drawable
	// =========================================================================

	@Override
	public float getZIndex() {
		return _zIndex;
	}

	public void setZIndex(float zIndex) {
		this._zIndex = zIndex;
	}

	@Override
	public boolean isVisible() {
		return _visible;
	}

	@Override
	public void show() {
		_visible = true;
	}

	@Override
	public void hide() {
		_visible = false;
	}
}

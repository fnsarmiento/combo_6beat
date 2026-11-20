package engine.objects;

/**
 * Contrato para objetos de escena que actualizan su lógica cada frame ({@link #update(float)}).
 */
public interface IUpdate {
	void update(float dt);
}

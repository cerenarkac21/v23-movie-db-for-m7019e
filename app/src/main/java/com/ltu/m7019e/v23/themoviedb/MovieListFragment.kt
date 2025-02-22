package com.ltu.m7019e.v23.themoviedb

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.ltu.m7019e.v23.themoviedb.adapter.MovieListAdapter
import com.ltu.m7019e.v23.themoviedb.adapter.MovieListClickListener
import com.ltu.m7019e.v23.themoviedb.databinding.FragmentMovieListBinding
import com.ltu.m7019e.v23.themoviedb.network.DataFetchStatus
import com.ltu.m7019e.v23.themoviedb.viewmodel.MovieListViewModel
import com.ltu.m7019e.v23.themoviedb.viewmodel.MovieListViewModelFactory

class MovieListFragment : Fragment() {
    private lateinit var viewModel: MovieListViewModel
    private lateinit var viewModelFactory: MovieListViewModelFactory

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    private var _binding: FragmentMovieListBinding? = null;
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentMovieListBinding.inflate(inflater)

        val container = TheMovieDataBase.getContainer(requireContext())
        val movieRepository = container.moviesRepository
        val application = requireNotNull(this.activity).application

        viewModelFactory = MovieListViewModelFactory(movieRepository, application)
        viewModel = ViewModelProvider(this, viewModelFactory)[MovieListViewModel::class.java]


        // set the layout manager as grid layout manager
        binding.movieListRv.layoutManager = GridLayoutManager(context, 4)

        val movieListAdapter = MovieListAdapter(
            MovieListClickListener { movie ->
                viewModel.onMovieListItemClicked(movie)
            })
        binding.movieListRv.adapter = movieListAdapter
        viewModel.movieList.observe(viewLifecycleOwner) { movieList ->
            movieList?.let {
                movieListAdapter.submitList(movieList)
            }
        }

        viewModel.navigateToMovieDetail.observe(viewLifecycleOwner) { movie ->
            movie?.let {
                this.findNavController().navigate(
                    MovieListFragmentDirections.actionMovieListFragmentToMovieDetailFragment(movie)
                )
                viewModel.onMovieDetailNavigated()
            }
        }

        viewModel.dataFetchStatus.observe(viewLifecycleOwner) { status ->
            status?.let {
                when (status) {
                    DataFetchStatus.LOADING -> {
                        binding.statusImage.visibility = View.VISIBLE
                        binding.statusImage.setImageResource(R.drawable.loading_animation)
                    }
                    DataFetchStatus.ERROR -> {
                        binding.statusImage.visibility = View.VISIBLE
                        binding.statusImage.setImageResource(R.drawable.ic_connection_error)
                    }
                    DataFetchStatus.DONE -> {
                        binding.statusImage.visibility = View.GONE
                    }
                }
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = requireActivity().findViewById<Toolbar>(R.id.toolbar)

        // cast the Activity to an AppCompatActivity.
        // Because the MainActivity class in main_activity.kt inherits AppCompatActivity.
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        val title = "Movie DB App"
        val actionBar = (activity as AppCompatActivity).supportActionBar
        actionBar?.title = title
        // DO NOT add the back button to the bar
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(false)

        // The usage of an interface lets you inject your own implementation
        val menuHost: MenuHost = requireActivity()

        // Add menu items without using the Fragment Menu APIs
        // Note how we can tie the MenuProvider to the viewLifecycleOwner
        // and an optional Lifecycle.State (here, RESUMED) to indicate when
        // the menu should be visible
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Add menu items here
                menuInflater.inflate(R.menu.menu_main, menu)

            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Handle the menu selection
                when (menuItem.itemId) {
                    R.id.action_load_popular_movies -> {
                        viewModel.getPopularMovies()
                    }
                    R.id.action_load_top_rated_movies -> {
                        viewModel.getTopRatedMovies()

                    }
                    R.id.action_load_saved_movies -> {
                        viewModel.getSavedMovies()
                    }
                }
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)


        // Initialize ConnectivityManager
        connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Create a NetworkCallback to monitor network connectivity changes
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                requireActivity().runOnUiThread {
                    // Network connection is available, refresh the fragment
                    refreshFragment()
                }
            }

            override fun onLost(network: Network) {
                requireActivity().runOnUiThread {
                    // Network connection is lost, show the "no connection" image
                    binding.statusImage.setImageResource(R.drawable.ic_connection_error)
                    binding.statusImage.visibility = View.VISIBLE
                }
            }
        }

        // Register the network callback
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Unregister the network callback when the fragment is destroyed
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun refreshFragment() {
        // Check if the "no connection" image is visible
        if (binding.statusImage.visibility == View.VISIBLE) {
            // Hide the "no connection" image
            binding.statusImage.visibility = View.GONE

            // Trigger the appropriate movie retrieval function based on the last fetched movies type
            when (viewModel.lastFetchedMoviesType) {
                MovieListViewModel.MoviesType.POPULAR -> viewModel.getPopularMovies()
                MovieListViewModel.MoviesType.TOP_RATED -> viewModel.getTopRatedMovies()
                MovieListViewModel.MoviesType.SAVED -> viewModel.getSavedMovies()
            }
        }
    }

}
